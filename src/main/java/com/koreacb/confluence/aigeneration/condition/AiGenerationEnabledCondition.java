package com.koreacb.confluence.aigeneration.condition;

import com.atlassian.plugin.web.Condition;
import com.atlassian.sal.api.component.ComponentLocator;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;

import java.util.Map;

public class AiGenerationEnabledCondition implements Condition {

    @Override
    public void init(Map<String, String> params) {
        // no initialization needed
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        AdminConfigService adminConfigService = ComponentLocator.getComponent(AdminConfigService.class);
        return adminConfigService != null && adminConfigService.isConfigured();
    }
}
