package com.koreacb.confluence.aigeneration.condition;

import com.atlassian.plugin.web.Condition;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
public class AiGenerationEnabledCondition implements Condition {
    private final AdminConfigService adminConfigService;

    @Inject
    public AiGenerationEnabledCondition(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    @Override
    public void init(Map<String, String> params) {
        // no initialization needed
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        return adminConfigService.isConfigured();
    }
}
