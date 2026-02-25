package com.koreacb.confluence.aigeneration.job;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.koreacb.confluence.aigeneration.ao.AoGenerationJob;
import com.koreacb.confluence.aigeneration.ao.AoGenerationSection;
import com.koreacb.confluence.aigeneration.model.JobStatus;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.AuditService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Calendar;
import java.util.Date;

/**
 * Periodic cleanup job that removes expired data:
 * - Completed/failed generation jobs older than retention period
 * - Old audit log entries
 * - Stale usage records
 */
@Named("cleanupJobRunner")
public class CleanupJobRunner implements JobRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CleanupJobRunner.class);

    private final ActiveObjects ao;
    private final AdminConfigService adminConfigService;
    private final AuditService auditService;

    @Inject
    public CleanupJobRunner(@ComponentImport ActiveObjects ao, AdminConfigService adminConfigService,
                            AuditService auditService) {
        this.ao = ao;
        this.adminConfigService = adminConfigService;
        this.auditService = auditService;
    }

    @Override
    public JobRunnerResponse runJob(JobRunnerRequest request) {
        LOG.info("Starting cleanup job");
        int totalCleaned = 0;

        try {
            int retentionDays = adminConfigService.getConfig().getAuditRetentionDays();
            if (retentionDays <= 0) retentionDays = 30;

            // 1. Clean up old completed/failed jobs and their sections
            totalCleaned += cleanupOldJobs(retentionDays);

            // 2. Clean up old audit log entries
            auditService.cleanupOldEntries(retentionDays);
            LOG.info("Cleaned audit log entries older than {} days", retentionDays);

            // 3. Clean up old usage records (keep 90 days)
            totalCleaned += cleanupOldUsageRecords(90);

            LOG.info("Cleanup job completed, total cleaned: {}", totalCleaned);
            return JobRunnerResponse.success("Cleaned " + totalCleaned + " records");

        } catch (Exception e) {
            LOG.error("Cleanup job failed", e);
            return JobRunnerResponse.failed(e);
        }
    }

    private int cleanupOldJobs(int retentionDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -retentionDays);
        Date cutoff = cal.getTime();

        int cleaned = 0;
        try {
            // Find old completed/failed/cancelled jobs
            AoGenerationJob[] oldJobs = ao.find(AoGenerationJob.class,
                    Query.select().where(
                            "(STATUS = ? OR STATUS = ? OR STATUS = ? OR STATUS = ?) AND COMPLETED_AT < ?",
                            JobStatus.COMPLETED.name(), JobStatus.FAILED.name(),
                            JobStatus.CANCELLED.name(), JobStatus.PARTIAL.name(),
                            cutoff));

            for (AoGenerationJob job : oldJobs) {
                try {
                    // Delete sections first
                    AoGenerationSection[] sections = job.getSections();
                    for (AoGenerationSection section : sections) {
                        ao.delete(section);
                    }
                    ao.delete(job);
                    cleaned++;
                } catch (Exception e) {
                    LOG.warn("Failed to delete job {}: {}", job.getJobUuid(), e.getMessage());
                }
            }

            if (cleaned > 0) {
                LOG.info("Cleaned {} old generation jobs", cleaned);
            }
        } catch (Exception e) {
            LOG.error("Error cleaning old jobs", e);
        }
        return cleaned;
    }

    private int cleanupOldUsageRecords(int retentionDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -retentionDays);
        Date cutoff = cal.getTime();

        int cleaned = 0;
        try {
            com.koreacb.confluence.aigeneration.ao.AoUsageRecord[] old = ao.find(
                    com.koreacb.confluence.aigeneration.ao.AoUsageRecord.class,
                    Query.select().where("RECORD_DATE < ?", cutoff));

            for (com.koreacb.confluence.aigeneration.ao.AoUsageRecord r : old) {
                ao.delete(r);
                cleaned++;
            }

            if (cleaned > 0) {
                LOG.info("Cleaned {} old usage records", cleaned);
            }
        } catch (Exception e) {
            LOG.error("Error cleaning usage records", e);
        }
        return cleaned;
    }
}
