package com.koreacb.confluence.aigeneration.ao;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;
import java.util.Date;

@Preload
@Table("AI_GEN_JOB")
public interface AoGenerationJob extends Entity {

    @Unique
    String getJobUuid();
    void setJobUuid(String jobUuid);

    @Indexed
    String getUserKey();
    void setUserKey(String userKey);

    @Indexed
    String getSpaceKey();
    void setSpaceKey(String spaceKey);

    String getTemplateKey();
    void setTemplateKey(String templateKey);

    @StringLength(StringLength.UNLIMITED)
    String getPurpose();
    void setPurpose(String purpose);

    String getAudience();
    void setAudience(String audience);

    String getTone();
    void setTone(String tone);

    String getLengthPreference();
    void setLengthPreference(String lengthPreference);

    @StringLength(StringLength.UNLIMITED)
    String getContextPageIds();
    void setContextPageIds(String contextPageIds);

    @StringLength(StringLength.UNLIMITED)
    String getAttachmentIds();
    void setAttachmentIds(String attachmentIds);

    @StringLength(StringLength.UNLIMITED)
    String getLabels();
    void setLabels(String labels);

    long getParentPageId();
    void setParentPageId(long parentPageId);

    @Indexed
    String getStatus();
    void setStatus(String status);

    int getTotalSections();
    void setTotalSections(int totalSections);

    int getCompletedSections();
    void setCompletedSections(int completedSections);

    String getCurrentSection();
    void setCurrentSection(String currentSection);

    int getTotalTokens();
    void setTotalTokens(int totalTokens);

    long getDurationMs();
    void setDurationMs(long durationMs);

    @StringLength(StringLength.UNLIMITED)
    String getErrorMessage();
    void setErrorMessage(String errorMessage);

    int getRetryCount();
    void setRetryCount(int retryCount);

    String getProcessingNodeId();
    void setProcessingNodeId(String processingNodeId);

    @Indexed
    Date getCreatedAt();
    void setCreatedAt(Date createdAt);

    Date getStartedAt();
    void setStartedAt(Date startedAt);

    Date getCompletedAt();
    void setCompletedAt(Date completedAt);

    long getResultPageId();
    void setResultPageId(long resultPageId);

    @OneToMany(reverse = "getJob")
    AoGenerationSection[] getSections();
}
