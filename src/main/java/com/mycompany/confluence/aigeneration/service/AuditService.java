package com.mycompany.confluence.aigeneration.service;

import com.mycompany.confluence.aigeneration.ao.AoAuditLog;
import java.util.Date;
import java.util.List;

public interface AuditService {
    void logAction(String userKey, String action, String spaceKey, long pageId, String details);
    List<AoAuditLog> queryAuditLog(String userKey, String spaceKey, String action,
                                    Date from, Date to, int offset, int limit);
    int getAuditLogCount(String userKey, String spaceKey, String action, Date from, Date to);
    void cleanupOldEntries(int retentionDays);
}
