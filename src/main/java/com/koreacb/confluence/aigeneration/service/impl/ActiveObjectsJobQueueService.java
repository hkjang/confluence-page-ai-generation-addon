package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.koreacb.confluence.aigeneration.ao.AoGenerationJob;
import com.koreacb.confluence.aigeneration.model.JobStatus;
import com.koreacb.confluence.aigeneration.service.JobQueueService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Calendar;
import java.util.Date;

@Named("jobQueueService")
public class ActiveObjectsJobQueueService implements JobQueueService {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveObjectsJobQueueService.class);
    private final ActiveObjects ao;

    @Inject
    public ActiveObjectsJobQueueService(@ComponentImport ActiveObjects ao) { this.ao = ao; }

    @Override
    public AoGenerationJob getJob(String jobUuid) {
        AoGenerationJob[] js = ao.find(AoGenerationJob.class, Query.select().where("JOB_UUID = ?", jobUuid));
        return js.length > 0 ? js[0] : null;
    }

    @Override
    public synchronized AoGenerationJob claimNextJob(String nodeId) {
        AoGenerationJob[] qs = ao.find(AoGenerationJob.class,
                Query.select().where("STATUS = ?", JobStatus.QUEUED.name()).order("CREATED_AT ASC").limit(1));
        if (qs.length == 0) return null;
        AoGenerationJob j = qs[0];
        // Re-read to verify status hasn't changed (optimistic concurrency check for cluster)
        ao.flushAll();
        AoGenerationJob[] verify = ao.find(AoGenerationJob.class,
                Query.select().where("JOB_UUID = ? AND STATUS = ?", j.getJobUuid(), JobStatus.QUEUED.name()));
        if (verify.length == 0) {
            LOG.debug("Job {} already claimed by another node", j.getJobUuid());
            return null;
        }
        j = verify[0];
        j.setStatus(JobStatus.IN_PROGRESS.name());
        j.setProcessingNodeId(nodeId); j.setStartedAt(new Date()); j.save();
        LOG.info("Claimed job {} on {}", j.getJobUuid(), nodeId);
        return j;
    }

    @Override
    public void updateJobStatus(String jobUuid, String status) {
        AoGenerationJob j = getJob(jobUuid);
        if (j != null) {
            j.setStatus(status);
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "PARTIAL".equals(status))
                j.setCompletedAt(new Date());
            j.save();
        }
    }

    @Override
    public int getActiveJobCount() {
        return ao.count(AoGenerationJob.class, Query.select().where("STATUS = ?", JobStatus.IN_PROGRESS.name()));
    }

    @Override
    public int getQueueDepth() {
        return ao.count(AoGenerationJob.class, Query.select().where("STATUS = ?", JobStatus.QUEUED.name()));
    }

    @Override
    public void recoverStaleJobs(int timeoutMinutes) {
        Calendar c = Calendar.getInstance(); c.add(Calendar.MINUTE, -timeoutMinutes);
        AoGenerationJob[] stale = ao.find(AoGenerationJob.class,
                Query.select().where("STATUS = ? AND STARTED_AT < ?", JobStatus.IN_PROGRESS.name(), c.getTime()));
        for (AoGenerationJob j : stale) {
            LOG.warn("Recovering stale job: {}", j.getJobUuid());
            j.setStatus(JobStatus.QUEUED.name()); j.setProcessingNodeId(null);
            j.setStartedAt(null); j.setRetryCount(j.getRetryCount() + 1); j.save();
        }
    }
}
