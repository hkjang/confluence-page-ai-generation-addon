package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.ao.AoSpacePolicy;
import java.util.List;

public interface PolicyService {
    boolean checkUserAllowed(String userKey, String spaceKey);
    boolean checkRateLimit(String userKey, String spaceKey);
    AoSpacePolicy getSpacePolicy(String spaceKey);
    void saveSpacePolicy(String spaceKey, int maxRequestsPerDay, int maxTokensPerRequest,
                         String allowedGroups, String allowedTemplates, boolean enabled);
    List<AoSpacePolicy> getAllPolicies();
    boolean isEnabledForSpace(String spaceKey);
}
