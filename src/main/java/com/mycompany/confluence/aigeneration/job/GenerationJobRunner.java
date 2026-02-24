package com.mycompany.confluence.aigeneration.job;

import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.google.gson.Gson;
import com.mycompany.confluence.aigeneration.ao.AoGenerationJob;
import com.mycompany.confluence.aigeneration.ao.AoGenerationSection;
import com.mycompany.confluence.aigeneration.model.*;
import com.mycompany.confluence.aigeneration.security.ContentSanitizer;
import com.mycompany.confluence.aigeneration.security.LogMasker;
import com.mycompany.confluence.aigeneration.security.RateLimiter;
import com.mycompany.confluence.aigeneration.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.util.*;

/**
 * Core job runner that processes queued generation jobs.
 * Polls every 5 seconds, claims a job, and generates content section-by-section.
 */
@Named("generationJobRunner")
public class GenerationJobRunner implements JobRunner {
    private static final Logger LOG = LoggerFactory.getLogger(GenerationJobRunner.class);
    private static final Gson GSON = new Gson();

    private final JobQueueService jobQueueService;
    private final VllmClientService vllmClient;
    private final PromptBuilderService promptBuilder;
    private final ContextBuilderService contextBuilder;
    private final PostProcessorService postProcessor;
    private final TemplateRegistryService templateRegistry;
    private final AdminConfigService adminConfigService;
    private final AuditService auditService;
    private final UsageTrackingService usageTracking;
    private final FallbackService fallbackService;
    private final RateLimiter rateLimiter;
    private final ContentSanitizer sanitizer;

    @Inject
    public GenerationJobRunner(JobQueueService jobQueueService, VllmClientService vllmClient,
                               PromptBuilderService promptBuilder, ContextBuilderService contextBuilder,
                               PostProcessorService postProcessor, TemplateRegistryService templateRegistry,
                               AdminConfigService adminConfigService, AuditService auditService,
                               UsageTrackingService usageTracking, FallbackService fallbackService,
                               RateLimiter rateLimiter, ContentSanitizer sanitizer) {
        this.jobQueueService = jobQueueService;
        this.vllmClient = vllmClient;
        this.promptBuilder = promptBuilder;
        this.contextBuilder = contextBuilder;
        this.postProcessor = postProcessor;
        this.templateRegistry = templateRegistry;
        this.adminConfigService = adminConfigService;
        this.auditService = auditService;
        this.usageTracking = usageTracking;
        this.fallbackService = fallbackService;
        this.rateLimiter = rateLimiter;
        this.sanitizer = sanitizer;
    }

    @Override
    public JobRunnerResponse runJob(JobRunnerRequest request) {
        if (!adminConfigService.isConfigured()) {
            return JobRunnerResponse.success("vLLM not configured, skipping");
        }

        String nodeId = getNodeId();
        if (!rateLimiter.tryAcquireNodeSlot()) {
            return JobRunnerResponse.success("Node concurrency limit reached");
        }

        try {
            // Recover stale jobs first
            jobQueueService.recoverStaleJobs(30);

            // Claim next job
            AoGenerationJob job = jobQueueService.claimNextJob(nodeId);
            if (job == null) {
                return JobRunnerResponse.success("No queued jobs");
            }

            LOG.info("Processing job {} for template {}", job.getJobUuid(), job.getTemplateKey());
            processJob(job);
            return JobRunnerResponse.success("Processed job: " + job.getJobUuid());

        } catch (Exception e) {
            LOG.error("Job runner error", e);
            return JobRunnerResponse.failed(e);
        } finally {
            rateLimiter.releaseNodeSlot();
        }
    }

    private void processJob(AoGenerationJob job) {
        long startTime = System.currentTimeMillis();
        int totalTokens = 0;
        int completedCount = 0;
        boolean hasFailures = false;

        try {
            DocumentTemplate template = templateRegistry.getTemplate(job.getTemplateKey());
            if (template == null) {
                failJob(job, "Template not found: " + job.getTemplateKey());
                return;
            }

            // Check if job was cancelled
            AoGenerationJob fresh = jobQueueService.getJob(job.getJobUuid());
            if (fresh == null || JobStatus.CANCELLED.name().equals(fresh.getStatus())) {
                LOG.info("Job {} was cancelled", job.getJobUuid());
                return;
            }

            // Build context
            PromptContext context = buildContext(job);

            // Build system prompt
            String systemPrompt = promptBuilder.buildSystemPrompt(job.getTemplateKey(), job.getSpaceKey());

            // Get sections sorted by order
            AoGenerationSection[] sections = job.getSections();
            Arrays.sort(sections, Comparator.comparingInt(AoGenerationSection::getOrderIndex));

            AdminConfig config = adminConfigService.getConfig();

            // Generate each section
            for (AoGenerationSection section : sections) {
                // Check cancellation before each section
                fresh = jobQueueService.getJob(job.getJobUuid());
                if (fresh == null || JobStatus.CANCELLED.name().equals(fresh.getStatus())) {
                    LOG.info("Job {} cancelled during processing", job.getJobUuid());
                    return;
                }

                // Update current section
                job.setCurrentSection(section.getSectionKey());
                job.save();

                try {
                    // Build section prompt
                    SectionDefinition secDef = template.getSectionDefinition(section.getSectionKey());
                    if (secDef == null) {
                        secDef = new SectionDefinition(section.getSectionKey(), null,
                                section.getSectionTitle(), null, false, section.getOrderIndex());
                    }

                    String sectionPrompt = promptBuilder.buildSectionPrompt(
                            secDef, context, job.getPurpose(), job.getAudience(),
                            job.getTone(), job.getLengthPreference());

                    // Call vLLM
                    VllmRequest vllmReq = new VllmRequest(
                            config.getModel(), systemPrompt, sectionPrompt,
                            config.getDefaultTemperature(), config.getMaxTokensPerRequest());

                    VllmResponse response = vllmClient.generateCompletion(vllmReq);

                    String content = response.getContent();
                    if (content == null || content.trim().isEmpty()) {
                        throw new RuntimeException("Empty response from vLLM for section: " + section.getSectionKey());
                    }

                    // Post-process content
                    content = postProcessor.convertToStorageFormat(content);
                    content = sanitizer.sanitize(content);
                    content = promptBuilder.filterForbiddenWords(content, job.getSpaceKey());

                    // Update section
                    section.setContent(content);
                    section.setStatus("COMPLETED");
                    section.setGeneratedAt(new Date());
                    if (response.getUsage() != null) {
                        section.setTokenCount(response.getUsage().getTotalTokens());
                        totalTokens += response.getUsage().getTotalTokens();
                    }
                    section.save();

                    completedCount++;
                    job.setCompletedSections(completedCount);
                    job.save();

                    LOG.info("Section {} completed for job {}", section.getSectionKey(), job.getJobUuid());

                } catch (Exception e) {
                    LOG.error("Failed to generate section {} for job {}: {}",
                            section.getSectionKey(), job.getJobUuid(), e.getMessage());

                    // Generate fallback content for this section
                    String fallbackContent = generateFallbackSection(section, template);
                    section.setContent(fallbackContent);
                    section.setStatus("FAILED");
                    section.setErrorMessage(LogMasker.truncateContent(e.getMessage()));
                    section.setGeneratedAt(new Date());
                    section.save();

                    hasFailures = true;
                    completedCount++;
                    job.setCompletedSections(completedCount);
                    job.save();
                }
            }

            // Finalize job
            long durationMs = System.currentTimeMillis() - startTime;
            job.setTotalTokens(totalTokens);
            job.setDurationMs(durationMs);
            job.setCompletedAt(new Date());
            job.setCurrentSection(null);

            if (hasFailures) {
                job.setStatus(JobStatus.PARTIAL.name());
            } else {
                job.setStatus(JobStatus.COMPLETED.name());
            }
            job.save();

            // Record usage
            usageTracking.recordUsage(job.getUserKey(), job.getSpaceKey(), totalTokens);

            // Audit log
            auditService.logAction(job.getUserKey(), "GENERATE_COMPLETED", job.getSpaceKey(), 0,
                    LogMasker.safeJobLog(job.getJobUuid(),
                            hasFailures ? "PARTIAL" : "COMPLETED",
                            job.getSpaceKey(), job.getTemplateKey()));

            LOG.info("Job {} finished: status={}, sections={}/{}, tokens={}, duration={}ms",
                    job.getJobUuid(), job.getStatus(), completedCount,
                    job.getTotalSections(), totalTokens, durationMs);

        } catch (Exception e) {
            LOG.error("Fatal error processing job {}", job.getJobUuid(), e);
            failJob(job, e.getMessage());

            // Try to generate a complete fallback
            try {
                DocumentTemplate template = templateRegistry.getTemplate(job.getTemplateKey());
                if (template != null) {
                    GenerationRequest fallbackReq = buildRequestFromJob(job);
                    GenerationResult fallbackResult = fallbackService.generateFallbackDraft(template, fallbackReq);
                    if (fallbackResult != null && fallbackResult.getSections() != null) {
                        AoGenerationSection[] sections = job.getSections();
                        for (GenerationSection fs : fallbackResult.getSections()) {
                            for (AoGenerationSection as : sections) {
                                if (as.getSectionKey().equals(fs.getKey())) {
                                    as.setContent(fs.getContent());
                                    as.setStatus("FALLBACK");
                                    as.save();
                                }
                            }
                        }
                        job.setStatus(JobStatus.PARTIAL.name());
                        job.save();
                    }
                }
            } catch (Exception fe) {
                LOG.error("Fallback also failed for job {}", job.getJobUuid(), fe);
            }
        }
    }

    private PromptContext buildContext(AoGenerationJob job) {
        try {
            GenerationRequest request = buildRequestFromJob(job);

            // Parse JSON arrays from job fields
            if (job.getContextPageIds() != null) {
                request.setContextPageIds(Arrays.asList(GSON.fromJson(job.getContextPageIds(), Long[].class)));
            }
            if (job.getAttachmentIds() != null) {
                request.setAttachmentIds(Arrays.asList(GSON.fromJson(job.getAttachmentIds(), Long[].class)));
            }
            if (job.getLabels() != null) {
                request.setLabels(Arrays.asList(GSON.fromJson(job.getLabels(), String[].class)));
            }

            return contextBuilder.buildPromptContext(request, job.getUserKey());
        } catch (Exception e) {
            LOG.warn("Failed to build context for job {}: {}", job.getJobUuid(), e.getMessage());
            return new PromptContext();
        }
    }

    private String generateFallbackSection(AoGenerationSection section, DocumentTemplate template) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(section.getSectionTitle()).append("</h2>\n");
        sb.append("<ac:structured-macro ac:name=\"info\">\n");
        sb.append("  <ac:rich-text-body>\n");
        sb.append("    <p>이 섹션은 AI 생성에 실패하여 기본 템플릿으로 대체되었습니다. 직접 내용을 작성해 주세요.</p>\n");
        sb.append("  </ac:rich-text-body>\n");
        sb.append("</ac:structured-macro>\n");

        SectionDefinition secDef = template.getSectionDefinition(section.getSectionKey());
        if (secDef != null && secDef.getPromptHint() != null) {
            sb.append("<p><em>작성 가이드: ").append(sanitizer.sanitize(secDef.getPromptHint())).append("</em></p>\n");
        }
        sb.append("<p>&nbsp;</p>\n");
        return sb.toString();
    }

    private void failJob(AoGenerationJob job, String errorMessage) {
        try {
            job.setStatus(JobStatus.FAILED.name());
            job.setErrorMessage(LogMasker.truncateContent(errorMessage));
            job.setCompletedAt(new Date());
            job.setDurationMs(System.currentTimeMillis() -
                    (job.getStartedAt() != null ? job.getStartedAt().getTime() : job.getCreatedAt().getTime()));
            job.save();

            auditService.logAction(job.getUserKey(), "GENERATE_FAILED", job.getSpaceKey(), 0,
                    LogMasker.safeJobLog(job.getJobUuid(), "FAILED", job.getSpaceKey(), job.getTemplateKey()));
        } catch (Exception e) {
            LOG.error("Failed to update job status to FAILED", e);
        }
    }

    private GenerationRequest buildRequestFromJob(AoGenerationJob job) {
        GenerationRequest req = new GenerationRequest();
        req.setSpaceKey(job.getSpaceKey());
        req.setTemplateKey(job.getTemplateKey());
        req.setPurpose(job.getPurpose());
        req.setAudience(job.getAudience());
        req.setTone(job.getTone());
        req.setLengthPreference(job.getLengthPreference());
        req.setParentPageId(job.getParentPageId());
        return req;
    }

    private String getNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
