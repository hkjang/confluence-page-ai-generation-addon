package com.mycompany.confluence.aigeneration.service.impl;

import com.mycompany.confluence.aigeneration.model.AdminConfig;
import com.mycompany.confluence.aigeneration.service.AdminConfigService;
import com.mycompany.confluence.aigeneration.service.EncryptionService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("adminConfigService")
public class BandanaAdminConfigService implements AdminConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(BandanaAdminConfigService.class);
    private static final String KEY_PREFIX = "com.mycompany.confluence.ai-page-generation.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final EncryptionService encryptionService;

    @Inject
    public BandanaAdminConfigService(PluginSettingsFactory pluginSettingsFactory,
                                     EncryptionService encryptionService) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.encryptionService = encryptionService;
    }

    @Override
    public AdminConfig getConfig() {
        PluginSettings s = pluginSettingsFactory.createGlobalSettings();
        AdminConfig c = new AdminConfig();
        c.setVllmEndpoint(gs(s, "vllm.endpoint"));
        c.setModel(gs(s, "vllm.model"));
        c.setApiKeySet(gs(s, "vllm.apiKey.encrypted") != null);
        c.setMaxTokensPerRequest(gi(s, "maxTokensPerRequest", 4096));
        c.setDefaultTemperature(gd(s, "defaultTemperature", 0.7));
        c.setTimeoutSeconds(gi(s, "timeoutSeconds", 60));
        c.setMaxConcurrentRequests(gi(s, "maxConcurrentRequests", 5));
        c.setStoragePolicy(gs(s, "storagePolicy", "NO_STORE"));
        c.setAuditRetentionDays(gi(s, "auditRetentionDays", 30));
        c.setMaxRequestsPerUserPerDay(gi(s, "maxRequestsPerUserPerDay", 50));
        c.setMaxRequestsPerSpacePerDay(gi(s, "maxRequestsPerSpacePerDay", 200));
        return c;
    }

    @Override
    public void saveConfig(AdminConfig config) {
        PluginSettings s = pluginSettingsFactory.createGlobalSettings();
        ps(s, "vllm.endpoint", config.getVllmEndpoint());
        ps(s, "vllm.model", config.getModel());
        ps(s, "maxTokensPerRequest", String.valueOf(config.getMaxTokensPerRequest()));
        ps(s, "defaultTemperature", String.valueOf(config.getDefaultTemperature()));
        ps(s, "timeoutSeconds", String.valueOf(config.getTimeoutSeconds()));
        ps(s, "maxConcurrentRequests", String.valueOf(config.getMaxConcurrentRequests()));
        ps(s, "storagePolicy", config.getStoragePolicy());
        ps(s, "auditRetentionDays", String.valueOf(config.getAuditRetentionDays()));
        ps(s, "maxRequestsPerUserPerDay", String.valueOf(config.getMaxRequestsPerUserPerDay()));
        ps(s, "maxRequestsPerSpacePerDay", String.valueOf(config.getMaxRequestsPerSpacePerDay()));
        LOG.info("Admin config saved");
    }

    @Override
    public String getDecryptedApiKey() {
        try {
            String enc = gs(pluginSettingsFactory.createGlobalSettings(), "vllm.apiKey.encrypted");
            return enc != null ? encryptionService.decrypt(enc) : null;
        } catch (Exception e) {
            LOG.error("Failed to decrypt API key", e);
            return null;
        }
    }

    @Override
    public void saveApiKey(String apiKey) {
        try {
            PluginSettings s = pluginSettingsFactory.createGlobalSettings();
            if (apiKey == null || apiKey.isEmpty()) {
                s.remove(KEY_PREFIX + "vllm.apiKey.encrypted");
            } else {
                ps(s, "vllm.apiKey.encrypted", encryptionService.encrypt(apiKey));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save API key", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return getConfig().isConfigured();
    }

    private String gs(PluginSettings s, String k) { return gs(s, k, null); }
    private String gs(PluginSettings s, String k, String d) {
        Object v = s.get(KEY_PREFIX + k);
        return v != null ? v.toString() : d;
    }
    private int gi(PluginSettings s, String k, int d) {
        String v = gs(s, k);
        try { return v != null ? Integer.parseInt(v) : d; } catch (Exception e) { return d; }
    }
    private double gd(PluginSettings s, String k, double d) {
        String v = gs(s, k);
        try { return v != null ? Double.parseDouble(v) : d; } catch (Exception e) { return d; }
    }
    private void ps(PluginSettings s, String k, String v) {
        if (v == null) s.remove(KEY_PREFIX + k); else s.put(KEY_PREFIX + k, v);
    }
}
