package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.ao.AoGenerationJob;

public interface JobQueueService {
    AoGenerationJob getJob(String jobUuid);
    AoGenerationJob claimNextJob(String nodeId);
    void updateJobStatus(String jobUuid, String status);
    int getActiveJobCount();
    int getQueueDepth();
    void recoverStaleJobs(int timeoutMinutes);
}
