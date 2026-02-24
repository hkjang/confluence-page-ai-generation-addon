package com.koreacb.confluence.aigeneration.model;

public class GenerationMetadata {
    private int tokenCount;
    private long durationMs;
    private String templateKey;
    private int totalSections;
    private int completedSections;

    public GenerationMetadata() {}

    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public int getTotalSections() { return totalSections; }
    public void setTotalSections(int totalSections) { this.totalSections = totalSections; }

    public int getCompletedSections() { return completedSections; }
    public void setCompletedSections(int completedSections) { this.completedSections = completedSections; }
}
