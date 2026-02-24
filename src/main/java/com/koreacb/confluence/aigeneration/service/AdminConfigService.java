package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.AdminConfig;

public interface AdminConfigService {
    AdminConfig getConfig();
    void saveConfig(AdminConfig config);
    String getDecryptedApiKey();
    void saveApiKey(String apiKey);
    boolean isConfigured();
}
