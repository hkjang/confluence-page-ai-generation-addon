package com.koreacb.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import java.util.Date;

@Preload
@Table("AI_GEN_SECTION")
public interface AoGenerationSection extends Entity {

    AoGenerationJob getJob();
    void setJob(AoGenerationJob job);

    String getSectionKey();
    void setSectionKey(String sectionKey);

    String getSectionTitle();
    void setSectionTitle(String sectionTitle);

    @StringLength(StringLength.UNLIMITED)
    String getContent();
    void setContent(String content);

    @Indexed
    String getStatus();
    void setStatus(String status);

    int getOrderIndex();
    void setOrderIndex(int orderIndex);

    int getTokenCount();
    void setTokenCount(int tokenCount);

    @StringLength(StringLength.UNLIMITED)
    String getErrorMessage();
    void setErrorMessage(String errorMessage);

    Date getGeneratedAt();
    void setGeneratedAt(Date generatedAt);
}
