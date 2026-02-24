package com.koreacb.confluence.aigeneration.condition;

import com.atlassian.confluence.spaces.Space;
import com.atlassian.plugin.web.Condition;
import com.koreacb.confluence.aigeneration.service.PolicyService;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
public class AiGenerationSpaceCondition implements Condition {
    private final PolicyService policyService;

    @Inject
    public AiGenerationSpaceCondition(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Override
    public void init(Map<String, String> params) {
        // no initialization needed
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        Object spaceObj = context.get("space");
        if (spaceObj instanceof Space) {
            Space space = (Space) spaceObj;
            return policyService.isEnabledForSpace(space.getKey());
        }
        return true; // If no space context, allow display
    }
}
