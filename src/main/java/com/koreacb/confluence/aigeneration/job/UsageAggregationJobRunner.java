package com.koreacb.confluence.aigeneration.job;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.scheduler.JobRunner;
import com.atlassian.scheduler.JobRunnerRequest;
import com.atlassian.scheduler.JobRunnerResponse;
import com.koreacb.confluence.aigeneration.ao.AoGenerationJob;
import com.koreacb.confluence.aigeneration.ao.AoUsageRecord;
import com.koreacb.confluence.aigeneration.model.JobStatus;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Hourly job that aggregates usage statistics from completed generation jobs.
 * Ensures AoUsageRecord entries are kept up-to-date with actual usage data.
 */
@Named("usageAggregationJobRunner")
public class UsageAggregationJobRunner implements JobRunner {
    private static final Logger LOG = LoggerFactory.getLogger(UsageAggregationJobRunner.class);

    private final ActiveObjects ao;

    @Inject
    public UsageAggregationJobRunner(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @Override
    public JobRunnerResponse runJob(JobRunnerRequest request) {
        LOG.debug("Starting usage aggregation job");

        try {
            // Get jobs completed in the last 2 hours (overlap to avoid gaps)
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR_OF_DAY, -2);
            Date since = cal.getTime();

            AoGenerationJob[] recentJobs = ao.find(AoGenerationJob.class,
                    Query.select().where(
                            "(STATUS = ? OR STATUS = ?) AND COMPLETED_AT > ?",
                            JobStatus.COMPLETED.name(), JobStatus.PARTIAL.name(), since));

            if (recentJobs.length == 0) {
                LOG.debug("No recent completed jobs to aggregate");
                return JobRunnerResponse.success("No jobs to aggregate");
            }

            // Group by user+space+date
            Map<String, AggregationEntry> aggregation = new HashMap<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            for (AoGenerationJob job : recentJobs) {
                String dateStr = dateFormat.format(job.getCompletedAt());
                String key = job.getUserKey() + "|" + job.getSpaceKey() + "|" + dateStr;

                AggregationEntry entry = aggregation.get(key);
                if (entry == null) {
                    entry = new AggregationEntry();
                    entry.userKey = job.getUserKey();
                    entry.spaceKey = job.getSpaceKey();
                    entry.dateStr = dateStr;
                    aggregation.put(key, entry);
                }
                entry.requestCount++;
                entry.totalTokens += job.getTotalTokens();
            }

            // Update or create usage records
            int updated = 0;
            for (AggregationEntry entry : aggregation.values()) {
                try {
                    updateUsageRecord(entry);
                    updated++;
                } catch (Exception e) {
                    LOG.warn("Failed to update usage for {}: {}", entry.userKey, e.getMessage());
                }
            }

            LOG.info("Usage aggregation completed: {} entries updated from {} jobs",
                    updated, recentJobs.length);
            return JobRunnerResponse.success("Aggregated " + updated + " entries");

        } catch (Exception e) {
            LOG.error("Usage aggregation failed", e);
            return JobRunnerResponse.failed(e);
        }
    }

    private void updateUsageRecord(AggregationEntry entry) {
        try {
            Date recordDate = new SimpleDateFormat("yyyy-MM-dd").parse(entry.dateStr);

            // Find existing record for this user/space/date
            Calendar dayStart = Calendar.getInstance();
            dayStart.setTime(recordDate);
            dayStart.set(Calendar.HOUR_OF_DAY, 0);
            dayStart.set(Calendar.MINUTE, 0);
            dayStart.set(Calendar.SECOND, 0);
            dayStart.set(Calendar.MILLISECOND, 0);

            Calendar dayEnd = Calendar.getInstance();
            dayEnd.setTime(dayStart.getTime());
            dayEnd.add(Calendar.DAY_OF_YEAR, 1);

            AoUsageRecord[] existing = ao.find(AoUsageRecord.class,
                    Query.select().where(
                            "USER_KEY = ? AND SPACE_KEY = ? AND RECORD_DATE >= ? AND RECORD_DATE < ?",
                            entry.userKey, entry.spaceKey, dayStart.getTime(), dayEnd.getTime()));

            if (existing.length > 0) {
                // Update if the aggregated count is higher
                AoUsageRecord rec = existing[0];
                if (entry.requestCount > rec.getRequestCount()) {
                    rec.setRequestCount(entry.requestCount);
                }
                if (entry.totalTokens > rec.getTotalTokens()) {
                    rec.setTotalTokens(entry.totalTokens);
                }
                rec.save();
            } else {
                // Create new record
                AoUsageRecord rec = ao.create(AoUsageRecord.class);
                rec.setUserKey(entry.userKey);
                rec.setSpaceKey(entry.spaceKey);
                rec.setRecordDate(dayStart.getTime());
                rec.setRequestCount(entry.requestCount);
                rec.setTotalTokens(entry.totalTokens);
                rec.save();
            }
        } catch (Exception e) {
            LOG.warn("Error updating usage record: {}", e.getMessage());
        }
    }

    private static class AggregationEntry {
        String userKey;
        String spaceKey;
        String dateStr;
        int requestCount;
        int totalTokens;
    }
}
