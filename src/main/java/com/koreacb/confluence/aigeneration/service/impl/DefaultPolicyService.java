package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.gson.Gson;
import com.koreacb.confluence.aigeneration.ao.AoSpacePolicy;
import com.koreacb.confluence.aigeneration.security.PermissionChecker;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.PolicyService;
import com.koreacb.confluence.aigeneration.service.UsageTrackingService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;

@ExportAsService({PolicyService.class})
@Named("policyService")
public class DefaultPolicyService implements PolicyService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPolicyService.class);
    private final ActiveObjects ao;
    private final AdminConfigService configService;
    private final UsageTrackingService usageService;
    private final PermissionChecker permChecker;
    private final Gson gson = new Gson();

    @Inject
    public DefaultPolicyService(@ComponentImport ActiveObjects ao, AdminConfigService configService,
                                UsageTrackingService usageService, PermissionChecker permChecker) {
        this.ao = ao; this.configService = configService;
        this.usageService = usageService; this.permChecker = permChecker;
    }

    @Override
    public boolean checkUserAllowed(String userKey, String spaceKey) {
        AoSpacePolicy p = getSpacePolicy(spaceKey);
        if (p == null) return true;
        if (!p.isEnabled()) return false;
        String g = p.getAllowedGroups();
        if (g == null || g.isEmpty()) return true;
        try {
            String[] groups = gson.fromJson(g, String[].class);
            if (groups == null || groups.length == 0) return true;
            for (String gr : groups) if (permChecker.isUserInGroup(userKey, gr)) return true;
            return false;
        } catch (Exception e) { return true; }
    }

    @Override
    public boolean checkRateLimit(String userKey, String spaceKey) {
        AoSpacePolicy p = getSpacePolicy(spaceKey);
        int max = (p != null && p.getMaxRequestsPerDay() > 0) ? p.getMaxRequestsPerDay()
                : configService.getConfig().getMaxRequestsPerUserPerDay();
        return usageService.getDailyRequestCount(userKey, spaceKey) < max;
    }

    @Override
    public AoSpacePolicy getSpacePolicy(String spaceKey) {
        if (spaceKey == null) return null;
        AoSpacePolicy[] ps = ao.find(AoSpacePolicy.class, Query.select().where("SPACE_KEY = ?", spaceKey));
        return ps.length > 0 ? ps[0] : null;
    }

    @Override
    public void saveSpacePolicy(String spaceKey, int maxReq, int maxTok,
                                String groups, String templates, boolean enabled) {
        AoSpacePolicy p = getSpacePolicy(spaceKey);
        if (p == null) { p = ao.create(AoSpacePolicy.class); p.setSpaceKey(spaceKey); }
        p.setMaxRequestsPerDay(maxReq); p.setMaxTokensPerRequest(maxTok);
        p.setAllowedGroups(groups); p.setAllowedTemplates(templates);
        p.setEnabled(enabled); p.save();
    }

    @Override
    public List<AoSpacePolicy> getAllPolicies() {
        return Arrays.asList(ao.find(AoSpacePolicy.class, Query.select()));
    }

    @Override
    public boolean isEnabledForSpace(String spaceKey) {
        AoSpacePolicy p = getSpacePolicy(spaceKey);
        return p == null || p.isEnabled();
    }
}
