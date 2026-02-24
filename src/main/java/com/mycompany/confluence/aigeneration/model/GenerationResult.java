package com.mycompany.confluence.aigeneration.model;

import java.util.List;

public class GenerationResult {
    private String jobId;
    private JobStatus status;
    private List<GenerationSection> sections;
    private QualityCheckResult qualityCheck;
    private GenerationMetadata metadata;

    public GenerationResult() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public List<GenerationSection> getSections() { return sections; }
    public void setSections(List<GenerationSection> sections) { this.sections = sections; }

    public QualityCheckResult getQualityCheck() { return qualityCheck; }
    public void setQualityCheck(QualityCheckResult qualityCheck) { this.qualityCheck = qualityCheck; }

    public GenerationMetadata getMetadata() { return metadata; }
    public void setMetadata(GenerationMetadata metadata) { this.metadata = metadata; }
}
