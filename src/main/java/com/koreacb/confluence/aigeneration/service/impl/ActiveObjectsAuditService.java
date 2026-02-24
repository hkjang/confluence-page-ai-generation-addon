package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.koreacb.confluence.aigeneration.ao.AoAuditLog;
import com.koreacb.confluence.aigeneration.security.LogMasker;
import com.koreacb.confluence.aigeneration.service.AuditService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@Named("auditService")
public class ActiveObjectsAuditService implements AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveObjectsAuditService.class);
    private final ActiveObjects ao;

    @Inject
    public ActiveObjectsAuditService(ActiveObjects ao) { this.ao = ao; }

    @Override
    public void logAction(String userKey, String action, String spaceKey, long pageId, String details) {
        try {
            AoAuditLog e = ao.create(AoAuditLog.class);
            e.setUserKey(userKey); e.setAction(action); e.setSpaceKey(spaceKey);
            e.setPageId(pageId); e.setDetails(LogMasker.truncateContent(details));
            e.setTimestamp(new Date()); e.save();
        } catch (Exception e) { LOG.error("Audit log failed: {}", action, e); }
    }

    @Override
    public List<AoAuditLog> queryAuditLog(String userKey, String spaceKey, String action,
                                           Date from, Date to, int offset, int limit) {
        StringBuilder w = new StringBuilder("1=1");
        List<Object> p = new ArrayList<>();
        if (userKey != null) { w.append(" AND USER_KEY = ?"); p.add(userKey); }
        if (spaceKey != null) { w.append(" AND SPACE_KEY = ?"); p.add(spaceKey); }
        if (action != null) { w.append(" AND ACTION = ?"); p.add(action); }
        if (from != null) { w.append(" AND TIMESTAMP >= ?"); p.add(from); }
        if (to != null) { w.append(" AND TIMESTAMP <= ?"); p.add(to); }
        return Arrays.asList(ao.find(AoAuditLog.class,
                Query.select().where(w.toString(), p.toArray()).order("TIMESTAMP DESC").offset(offset).limit(limit)));
    }

    @Override
    public int getAuditLogCount(String userKey, String spaceKey, String action, Date from, Date to) {
        StringBuilder w = new StringBuilder("1=1");
        List<Object> p = new ArrayList<>();
        if (userKey != null) { w.append(" AND USER_KEY = ?"); p.add(userKey); }
        if (spaceKey != null) { w.append(" AND SPACE_KEY = ?"); p.add(spaceKey); }
        if (action != null) { w.append(" AND ACTION = ?"); p.add(action); }
        if (from != null) { w.append(" AND TIMESTAMP >= ?"); p.add(from); }
        if (to != null) { w.append(" AND TIMESTAMP <= ?"); p.add(to); }
        return ao.count(AoAuditLog.class, Query.select().where(w.toString(), p.toArray()));
    }

    @Override
    public void cleanupOldEntries(int retentionDays) {
        Calendar c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, -retentionDays);
        AoAuditLog[] old = ao.find(AoAuditLog.class, Query.select().where("TIMESTAMP < ?", c.getTime()));
        for (AoAuditLog e : old) ao.delete(e);
        if (old.length > 0) LOG.info("Cleaned {} audit entries", old.length);
    }
}
