package com.koreacb.confluence.aigeneration.condition;

import com.atlassian.confluence.spaces.Space;
import com.atlassian.plugin.web.Condition;
import com.atlassian.sal.api.component.ComponentLocator;
import com.koreacb.confluence.aigeneration.service.PolicyService;

import java.util.Map;

public class AiGenerationSpaceCondition implements Condition {

    @Override
    public void init(Map<String, String> params) {
        // no initialization needed
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        PolicyService policyService = ComponentLocator.getComponent(PolicyService.class);
        if (policyService == null) {
            return true;
        }
        Object spaceObj = context.get("space");
        if (spaceObj instanceof Space) {
            Space space = (Space) spaceObj;
            return policyService.isEnabledForSpace(space.getKey());
        }
        return true;
    }
}
