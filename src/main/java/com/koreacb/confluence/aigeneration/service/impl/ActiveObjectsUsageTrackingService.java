package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.koreacb.confluence.aigeneration.ao.AoUsageRecord;
import com.koreacb.confluence.aigeneration.service.UsageTrackingService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@Named("usageTrackingService")
public class ActiveObjectsUsageTrackingService implements UsageTrackingService {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveObjectsUsageTrackingService.class);
    private final ActiveObjects ao;

    @Inject
    public ActiveObjectsUsageTrackingService(@ComponentImport ActiveObjects ao) { this.ao = ao; }

    @Override
    public synchronized void recordUsage(String userKey, String spaceKey, int tokenCount) {
        try {
            Date today = today();
            AoUsageRecord[] rs = ao.find(AoUsageRecord.class,
                    Query.select().where("USER_KEY = ? AND SPACE_KEY = ? AND RECORD_DATE = ?", userKey, spaceKey, today));
            AoUsageRecord r;
            if (rs.length > 0) {
                r = rs[0]; r.setRequestCount(r.getRequestCount() + 1);
                r.setTotalTokens(r.getTotalTokens() + tokenCount);
            } else {
                r = ao.create(AoUsageRecord.class);
                r.setUserKey(userKey); r.setSpaceKey(spaceKey); r.setRecordDate(today);
                r.setRequestCount(1); r.setTotalTokens(tokenCount);
            }
            r.save();
        } catch (Exception e) { LOG.error("Record usage failed", e); }
    }

    @Override
    public int getDailyRequestCount(String userKey, String spaceKey) {
        try {
            AoUsageRecord[] rs = ao.find(AoUsageRecord.class,
                    Query.select().where("USER_KEY = ? AND RECORD_DATE = ?", userKey, today()));
            int t = 0;
            for (AoUsageRecord r : rs)
                if (spaceKey == null || spaceKey.equals(r.getSpaceKey())) t += r.getRequestCount();
            return t;
        } catch (Exception e) { return 0; }
    }

    @Override
    public int getDailyTokenCount(String userKey, String spaceKey) {
        try {
            AoUsageRecord[] rs = ao.find(AoUsageRecord.class,
                    Query.select().where("USER_KEY = ? AND RECORD_DATE = ?", userKey, today()));
            int t = 0;
            for (AoUsageRecord r : rs)
                if (spaceKey == null || spaceKey.equals(r.getSpaceKey())) t += r.getTotalTokens();
            return t;
        } catch (Exception e) { return 0; }
    }

    @Override
    public Map<String, Object> getUsageSummary(String userKey, String spaceKey, Date from, Date to) {
        Map<String, Object> s = new LinkedHashMap<>();
        StringBuilder w = new StringBuilder("1=1");
        List<Object> p = new ArrayList<>();
        if (userKey != null) { w.append(" AND USER_KEY = ?"); p.add(userKey); }
        if (spaceKey != null) { w.append(" AND SPACE_KEY = ?"); p.add(spaceKey); }
        if (from != null) { w.append(" AND RECORD_DATE >= ?"); p.add(from); }
        if (to != null) { w.append(" AND RECORD_DATE <= ?"); p.add(to); }
        AoUsageRecord[] rs = ao.find(AoUsageRecord.class, Query.select().where(w.toString(), p.toArray()));
        int totalReq = 0, totalTok = 0;
        Set<String> users = new HashSet<>(), spaces = new HashSet<>();
        for (AoUsageRecord r : rs) {
            totalReq += r.getRequestCount(); totalTok += r.getTotalTokens();
            users.add(r.getUserKey()); if (r.getSpaceKey() != null) spaces.add(r.getSpaceKey());
        }
        s.put("totalRequests", totalReq); s.put("totalTokens", totalTok);
        s.put("uniqueUsers", users.size()); s.put("uniqueSpaces", spaces.size());
        return s;
    }

    private Date today() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
