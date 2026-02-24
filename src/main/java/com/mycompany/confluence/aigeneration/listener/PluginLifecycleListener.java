package com.mycompany.confluence.aigeneration.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.scheduler.SchedulerService;
import com.atlassian.scheduler.SchedulerServiceException;
import com.atlassian.scheduler.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Manages plugin lifecycle: registers/unregisters scheduled jobs
 * when the plugin is enabled/disabled.
 */
@Named
public class PluginLifecycleListener implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(PluginLifecycleListener.class);

    private static final String PLUGIN_KEY = "com.mycompany.confluence.ai-page-generation";

    // Job runner keys
    private static final JobRunnerKey GENERATION_JOB_KEY =
            JobRunnerKey.of("com.mycompany.confluence.ai-page-generation:generationJobRunner");
    private static final JobRunnerKey CLEANUP_JOB_KEY =
            JobRunnerKey.of("com.mycompany.confluence.ai-page-generation:cleanupJobRunner");
    private static final JobRunnerKey USAGE_AGGREGATION_JOB_KEY =
            JobRunnerKey.of("com.mycompany.confluence.ai-page-generation:usageAggregationJobRunner");

    // Job IDs
    private static final JobId GENERATION_JOB_ID = JobId.of("ai-generation-job-processor");
    private static final JobId CLEANUP_JOB_ID = JobId.of("ai-generation-cleanup-job");
    private static final JobId USAGE_AGGREGATION_JOB_ID = JobId.of("ai-generation-usage-aggregation-job");

    private final EventPublisher eventPublisher;
    private final SchedulerService schedulerService;

    @Inject
    public PluginLifecycleListener(EventPublisher eventPublisher,
                                   SchedulerService schedulerService) {
        this.eventPublisher = eventPublisher;
        this.schedulerService = schedulerService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
        registerJobs();
    }

    @Override
    public void destroy() throws Exception {
        unregisterJobs();
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        if (PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            LOG.info("AI Generation plugin enabled, registering scheduled jobs");
            registerJobs();
        }
    }

    @EventListener
    public void onPluginDisabled(PluginDisabledEvent event) {
        if (PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            LOG.info("AI Generation plugin disabled, unregistering scheduled jobs");
            unregisterJobs();
        }
    }

    private void registerJobs() {
        try {
            // Generation Job: polls every 5 seconds for queued generation tasks
            Schedule generationSchedule = Schedule.forInterval(5000L, null);
            JobConfig generationConfig = JobConfig.forJobRunnerKey(GENERATION_JOB_KEY)
                    .withSchedule(generationSchedule)
                    .withRunMode(RunMode.RUN_ONCE_PER_CLUSTER);
            schedulerService.scheduleJob(GENERATION_JOB_ID, generationConfig);
            LOG.info("Registered generation job runner (5s interval)");
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to register generation job runner", e);
        }

        try {
            // Cleanup Job: runs daily at 2:00 AM - use interval of 24 hours
            Schedule cleanupSchedule = Schedule.forInterval(86400000L, null);
            JobConfig cleanupConfig = JobConfig.forJobRunnerKey(CLEANUP_JOB_KEY)
                    .withSchedule(cleanupSchedule)
                    .withRunMode(RunMode.RUN_ONCE_PER_CLUSTER);
            schedulerService.scheduleJob(CLEANUP_JOB_ID, cleanupConfig);
            LOG.info("Registered cleanup job runner (daily)");
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to register cleanup job runner", e);
        }

        try {
            // Usage Aggregation Job: runs every hour
            Schedule usageSchedule = Schedule.forInterval(3600000L, null);
            JobConfig usageConfig = JobConfig.forJobRunnerKey(USAGE_AGGREGATION_JOB_KEY)
                    .withSchedule(usageSchedule)
                    .withRunMode(RunMode.RUN_ONCE_PER_CLUSTER);
            schedulerService.scheduleJob(USAGE_AGGREGATION_JOB_ID, usageConfig);
            LOG.info("Registered usage aggregation job runner (hourly)");
        } catch (SchedulerServiceException e) {
            LOG.error("Failed to register usage aggregation job runner", e);
        }
    }

    private void unregisterJobs() {
        try { schedulerService.unscheduleJob(GENERATION_JOB_ID); } catch (Exception e) { LOG.debug("Unschedule generation: {}", e.getMessage()); }
        try { schedulerService.unscheduleJob(CLEANUP_JOB_ID); } catch (Exception e) { LOG.debug("Unschedule cleanup: {}", e.getMessage()); }
        try { schedulerService.unscheduleJob(USAGE_AGGREGATION_JOB_ID); } catch (Exception e) { LOG.debug("Unschedule usage: {}", e.getMessage()); }
        LOG.info("Unregistered all scheduled jobs");
    }
}
