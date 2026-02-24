package com.mycompany.confluence.aigeneration.model;

import java.util.List;

public class GenerationRequest {
    private String spaceKey;
    private String templateKey;
    private String purpose;
    private String audience;
    private String tone;
    private String lengthPreference;
    private List<Long> contextPageIds;
    private List<Long> attachmentIds;
    private List<String> labels;
    private long parentPageId;
    private String additionalContext;

    public GenerationRequest() {}

    public String getSpaceKey() { return spaceKey; }
    public void setSpaceKey(String spaceKey) { this.spaceKey = spaceKey; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getLengthPreference() { return lengthPreference; }
    public void setLengthPreference(String lengthPreference) { this.lengthPreference = lengthPreference; }

    public List<Long> getContextPageIds() { return contextPageIds; }
    public void setContextPageIds(List<Long> contextPageIds) { this.contextPageIds = contextPageIds; }

    public List<Long> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<Long> attachmentIds) { this.attachmentIds = attachmentIds; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public long getParentPageId() { return parentPageId; }
    public void setParentPageId(long parentPageId) { this.parentPageId = parentPageId; }

    public String getAdditionalContext() { return additionalContext; }
    public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }
}
