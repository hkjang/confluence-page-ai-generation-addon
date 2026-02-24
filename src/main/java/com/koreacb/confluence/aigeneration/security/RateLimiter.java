package com.koreacb.confluence.aigeneration.security;

import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.UsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Named("rateLimiter")
public class RateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

    private final AdminConfigService adminConfigService;
    private final UsageTrackingService usageTrackingService;
    private final ConcurrentHashMap<String, AtomicInteger> userConcurrentMap = new ConcurrentHashMap<>();
    private volatile Semaphore nodeSlotSemaphore;
    private volatile int lastMaxConcurrent = 0;

    @Inject
    public RateLimiter(AdminConfigService adminConfigService,
                       UsageTrackingService usageTrackingService) {
        this.adminConfigService = adminConfigService;
        this.usageTrackingService = usageTrackingService;
    }

    public boolean tryAcquireNodeSlot() {
        return getNodeSemaphore().tryAcquire();
    }

    public void releaseNodeSlot() {
        getNodeSemaphore().release();
    }

    public boolean tryAcquireUserSlot(String userKey) {
        AtomicInteger counter = userConcurrentMap.computeIfAbsent(userKey, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > 3) { counter.decrementAndGet(); return false; }
        return true;
    }

    public void releaseUserSlot(String userKey) {
        AtomicInteger counter = userConcurrentMap.get(userKey);
        if (counter != null) {
            if (counter.decrementAndGet() <= 0) userConcurrentMap.remove(userKey);
        }
    }

    public boolean checkDailyLimit(String userKey, String spaceKey) {
        int maxPerDay = adminConfigService.getConfig().getMaxRequestsPerUserPerDay();
        int currentCount = usageTrackingService.getDailyRequestCount(userKey, spaceKey);
        return currentCount < maxPerDay;
    }

    private synchronized Semaphore getNodeSemaphore() {
        int maxConcurrent = adminConfigService.getConfig().getMaxConcurrentRequests();
        if (nodeSlotSemaphore == null || maxConcurrent != lastMaxConcurrent) {
            nodeSlotSemaphore = new Semaphore(maxConcurrent);
            lastMaxConcurrent = maxConcurrent;
        }
        return nodeSlotSemaphore;
    }
}
