package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.google.gson.Gson;
import com.koreacb.confluence.aigeneration.ao.AoGenerationJob;
import com.koreacb.confluence.aigeneration.ao.AoGenerationSection;
import com.koreacb.confluence.aigeneration.model.*;
import com.koreacb.confluence.aigeneration.security.LogMasker;
import com.koreacb.confluence.aigeneration.security.PermissionChecker;
import com.koreacb.confluence.aigeneration.security.RateLimiter;
import com.koreacb.confluence.aigeneration.service.*;
import com.atlassian.confluence.user.ConfluenceUser;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@Named("aiGenerationService")
public class DefaultAiGenerationService implements AiGenerationService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAiGenerationService.class);
    private final ActiveObjects ao;
    private final PermissionChecker permChecker;
    private final PolicyService policyService;
    private final RateLimiter rateLimiter;
    private final TemplateRegistryService templateService;
    private final AuditService auditService;
    private final Gson gson = new Gson();

    @Inject
    public DefaultAiGenerationService(ActiveObjects ao, PermissionChecker permChecker,
                                      PolicyService policyService, RateLimiter rateLimiter,
                                      TemplateRegistryService templateService, AuditService auditService) {
        this.ao = ao; this.permChecker = permChecker; this.policyService = policyService;
        this.rateLimiter = rateLimiter; this.templateService = templateService; this.auditService = auditService;
    }

    @Override
    public String submitGenerationJob(GenerationRequest req, String userKey) throws Exception {
        if (req.getTemplateKey() == null) throw new IllegalArgumentException("Template key required");
        if (req.getSpaceKey() == null) throw new IllegalArgumentException("Space key required");
        DocumentTemplate tmpl = templateService.getTemplate(req.getTemplateKey());
        if (tmpl == null) throw new IllegalArgumentException("Template not found: " + req.getTemplateKey());
        ConfluenceUser user = permChecker.getUser(userKey);
        if (user == null) throw new SecurityException("User not found: " + userKey);
        if (!permChecker.canUserGenerate(user, req.getSpaceKey()))
            throw new SecurityException("No permission for space: " + req.getSpaceKey());
        if (!policyService.checkUserAllowed(userKey, req.getSpaceKey()))
            throw new SecurityException("Not allowed by space policy");
        if (!policyService.checkRateLimit(userKey, req.getSpaceKey()))
            throw new IllegalStateException("Rate limit exceeded");
        if (!rateLimiter.checkDailyLimit(userKey, req.getSpaceKey()))
            throw new IllegalStateException("Daily request limit exceeded");
        if (!rateLimiter.tryAcquireUserSlot(userKey))
            throw new IllegalStateException("Too many concurrent requests for user");

        String jobUuid = UUID.randomUUID().toString();
        AoGenerationJob job = ao.create(AoGenerationJob.class);
        job.setJobUuid(jobUuid); job.setUserKey(userKey); job.setSpaceKey(req.getSpaceKey());
        job.setTemplateKey(req.getTemplateKey()); job.setPurpose(req.getPurpose());
        job.setAudience(req.getAudience()); job.setTone(req.getTone());
        job.setLengthPreference(req.getLengthPreference());
        job.setContextPageIds(req.getContextPageIds() != null ? gson.toJson(req.getContextPageIds()) : null);
        job.setAttachmentIds(req.getAttachmentIds() != null ? gson.toJson(req.getAttachmentIds()) : null);
        job.setLabels(req.getLabels() != null ? gson.toJson(req.getLabels()) : null);
        job.setParentPageId(req.getParentPageId());
        job.setStatus(JobStatus.QUEUED.name()); job.setCreatedAt(new Date());
        List<SectionDefinition> secs = tmpl.getAllSections();
        job.setTotalSections(secs.size()); job.setCompletedSections(0); job.save();

        for (int i = 0; i < secs.size(); i++) {
            SectionDefinition sd = secs.get(i);
            AoGenerationSection s = ao.create(AoGenerationSection.class);
            s.setJob(job); s.setSectionKey(sd.getKey()); s.setSectionTitle(sd.getDefaultTitle());
            s.setStatus("PENDING"); s.setOrderIndex(i); s.save();
        }
        auditService.logAction(userKey, "GENERATE_SUBMITTED", req.getSpaceKey(), 0,
                LogMasker.safeJobLog(jobUuid, "QUEUED", req.getSpaceKey(), req.getTemplateKey()));
        return jobUuid;
    }

    @Override
    public JobStatus getJobStatus(String jobId) {
        AoGenerationJob j = findJob(jobId);
        return j != null ? JobStatus.valueOf(j.getStatus()) : null;
    }

    @Override
    public GenerationResult getJobResult(String jobId) {
        AoGenerationJob j = findJob(jobId);
        if (j == null) return null;
        GenerationResult r = new GenerationResult();
        r.setJobId(jobId); r.setStatus(JobStatus.valueOf(j.getStatus()));
        List<GenerationSection> sections = new ArrayList<>();
        AoGenerationSection[] as = j.getSections();
        Arrays.sort(as, Comparator.comparingInt(AoGenerationSection::getOrderIndex));
        for (AoGenerationSection a : as)
            sections.add(new GenerationSection(a.getSectionKey(), a.getSectionTitle(), a.getContent(), a.getStatus()));
        r.setSections(sections);
        GenerationMetadata m = new GenerationMetadata();
        m.setTokenCount(j.getTotalTokens()); m.setDurationMs(j.getDurationMs());
        m.setTemplateKey(j.getTemplateKey()); m.setTotalSections(j.getTotalSections());
        m.setCompletedSections(j.getCompletedSections());
        r.setMetadata(m);
        return r;
    }

    @Override
    public void cancelJob(String jobId, String userKey) {
        AoGenerationJob j = findJob(jobId);
        if (j == null) throw new IllegalArgumentException("Job not found");
        if (!j.getUserKey().equals(userKey) && !permChecker.canUserAdmin(permChecker.getUser(userKey)))
            throw new SecurityException("Not authorized");
        j.setStatus(JobStatus.CANCELLED.name()); j.setCompletedAt(new Date()); j.save();
        auditService.logAction(userKey, "GENERATE_CANCELLED", j.getSpaceKey(), 0, "jobId=" + jobId);
    }

    @Override
    public String retryJob(String jobId, String userKey, List<String> sectionsToRetry) throws Exception {
        AoGenerationJob o = findJob(jobId);
        if (o == null) throw new IllegalArgumentException("Job not found");
        GenerationRequest req = new GenerationRequest();
        req.setSpaceKey(o.getSpaceKey()); req.setTemplateKey(o.getTemplateKey());
        req.setPurpose(o.getPurpose()); req.setAudience(o.getAudience());
        req.setTone(o.getTone()); req.setLengthPreference(o.getLengthPreference());
        req.setParentPageId(o.getParentPageId());
        if (o.getContextPageIds() != null) req.setContextPageIds(Arrays.asList(gson.fromJson(o.getContextPageIds(), Long[].class)));
        if (o.getAttachmentIds() != null) req.setAttachmentIds(Arrays.asList(gson.fromJson(o.getAttachmentIds(), Long[].class)));
        return submitGenerationJob(req, userKey);
    }

    @Override
    public double getJobProgress(String jobId) {
        AoGenerationJob j = findJob(jobId);
        return (j != null && j.getTotalSections() > 0) ? (double) j.getCompletedSections() / j.getTotalSections() : 0;
    }

    @Override
    public String getJobOwner(String jobId) {
        AoGenerationJob j = findJob(jobId);
        return j != null ? j.getUserKey() : null;
    }

    private AoGenerationJob findJob(String uuid) {
        AoGenerationJob[] js = ao.find(AoGenerationJob.class, Query.select().where("JOB_UUID = ?", uuid));
        return js.length > 0 ? js[0] : null;
    }
}
