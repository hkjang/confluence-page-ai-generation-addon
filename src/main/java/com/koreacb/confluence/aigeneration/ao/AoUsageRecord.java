package com.koreacb.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.Table;
import java.util.Date;

@Table("AI_GEN_USAGE")
public interface AoUsageRecord extends Entity {

    @Indexed
    String getUserKey();
    void setUserKey(String userKey);

    @Indexed
    String getSpaceKey();
    void setSpaceKey(String spaceKey);

    @Indexed
    Date getRecordDate();
    void setRecordDate(Date recordDate);

    int getRequestCount();
    void setRequestCount(int requestCount);

    int getTotalTokens();
    void setTotalTokens(int totalTokens);
}
