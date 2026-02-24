package com.mycompany.confluence.aigeneration.service;

import com.mycompany.confluence.aigeneration.model.GenerationRequest;
import com.mycompany.confluence.aigeneration.model.GenerationResult;
import com.mycompany.confluence.aigeneration.model.JobStatus;
import java.util.List;

public interface AiGenerationService {
    String submitGenerationJob(GenerationRequest request, String userKey) throws Exception;
    JobStatus getJobStatus(String jobId);
    GenerationResult getJobResult(String jobId);
    void cancelJob(String jobId, String userKey);
    String retryJob(String jobId, String userKey, List<String> sectionsToRetry) throws Exception;
    double getJobProgress(String jobId);
    String getJobOwner(String jobId);
}
