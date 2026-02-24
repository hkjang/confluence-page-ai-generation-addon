package com.mycompany.confluence.aigeneration.model;

public class AdminConfig {
    private String vllmEndpoint;
    private String model;
    private boolean apiKeySet;
    private int maxTokensPerRequest;
    private double defaultTemperature;
    private int timeoutSeconds;
    private int maxConcurrentRequests;
    private String storagePolicy;
    private int auditRetentionDays;
    private int maxRequestsPerUserPerDay;
    private int maxRequestsPerSpacePerDay;

    public AdminConfig() {
        this.maxTokensPerRequest = 4096;
        this.defaultTemperature = 0.7;
        this.timeoutSeconds = 60;
        this.maxConcurrentRequests = 5;
        this.storagePolicy = "NO_STORE";
        this.auditRetentionDays = 30;
        this.maxRequestsPerUserPerDay = 50;
        this.maxRequestsPerSpacePerDay = 200;
    }

    public String getVllmEndpoint() { return vllmEndpoint; }
    public void setVllmEndpoint(String vllmEndpoint) { this.vllmEndpoint = vllmEndpoint; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isApiKeySet() { return apiKeySet; }
    public void setApiKeySet(boolean apiKeySet) { this.apiKeySet = apiKeySet; }

    public int getMaxTokensPerRequest() { return maxTokensPerRequest; }
    public void setMaxTokensPerRequest(int maxTokensPerRequest) { this.maxTokensPerRequest = maxTokensPerRequest; }

    public double getDefaultTemperature() { return defaultTemperature; }
    public void setDefaultTemperature(double defaultTemperature) { this.defaultTemperature = defaultTemperature; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }

    public String getStoragePolicy() { return storagePolicy; }
    public void setStoragePolicy(String storagePolicy) { this.storagePolicy = storagePolicy; }

    public int getAuditRetentionDays() { return auditRetentionDays; }
    public void setAuditRetentionDays(int auditRetentionDays) { this.auditRetentionDays = auditRetentionDays; }

    public int getMaxRequestsPerUserPerDay() { return maxRequestsPerUserPerDay; }
    public void setMaxRequestsPerUserPerDay(int maxRequestsPerUserPerDay) { this.maxRequestsPerUserPerDay = maxRequestsPerUserPerDay; }

    public int getMaxRequestsPerSpacePerDay() { return maxRequestsPerSpacePerDay; }
    public void setMaxRequestsPerSpacePerDay(int maxRequestsPerSpacePerDay) { this.maxRequestsPerSpacePerDay = maxRequestsPerSpacePerDay; }

    public boolean isConfigured() {
        return vllmEndpoint != null && !vllmEndpoint.isEmpty()
                && model != null && !model.isEmpty();
    }
}
