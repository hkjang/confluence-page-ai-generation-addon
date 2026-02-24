package com.koreacb.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import java.util.Date;

@Table("AI_GEN_AUDIT")
public interface AoAuditLog extends Entity {

    @Indexed
    String getUserKey();
    void setUserKey(String userKey);

    @Indexed
    String getAction();
    void setAction(String action);

    String getSpaceKey();
    void setSpaceKey(String spaceKey);

    long getPageId();
    void setPageId(long pageId);

    String getJobUuid();
    void setJobUuid(String jobUuid);

    @StringLength(StringLength.UNLIMITED)
    String getDetails();
    void setDetails(String details);

    @Indexed
    Date getTimestamp();
    void setTimestamp(Date timestamp);

    String getNodeId();
    void setNodeId(String nodeId);
}
