package com.koreacb.confluence.aigeneration.service;

import java.util.Date;
import java.util.Map;

public interface UsageTrackingService {
    void recordUsage(String userKey, String spaceKey, int tokenCount);
    int getDailyRequestCount(String userKey, String spaceKey);
    int getDailyTokenCount(String userKey, String spaceKey);
    Map<String, Object> getUsageSummary(String userKey, String spaceKey, Date from, Date to);
}
