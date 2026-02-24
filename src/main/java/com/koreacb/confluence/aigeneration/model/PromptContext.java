package com.koreacb.confluence.aigeneration.model;

import java.util.List;
import java.util.Map;

public class PromptContext {
    private Map<Long, String> pageContents;
    private Map<Long, String> attachmentTexts;
    private List<String> labels;
    private String spaceDescription;
    private String additionalContext;

    public PromptContext() {}

    public Map<Long, String> getPageContents() { return pageContents; }
    public void setPageContents(Map<Long, String> pageContents) { this.pageContents = pageContents; }

    public Map<Long, String> getAttachmentTexts() { return attachmentTexts; }
    public void setAttachmentTexts(Map<Long, String> attachmentTexts) { this.attachmentTexts = attachmentTexts; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public String getSpaceDescription() { return spaceDescription; }
    public void setSpaceDescription(String spaceDescription) { this.spaceDescription = spaceDescription; }

    public String getAdditionalContext() { return additionalContext; }
    public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }

    public String toContextString(int maxLength) {
        StringBuilder sb = new StringBuilder();
        if (pageContents != null) {
            for (Map.Entry<Long, String> entry : pageContents.entrySet()) {
                sb.append("--- Page ").append(entry.getKey()).append(" ---\n");
                sb.append(entry.getValue()).append("\n\n");
            }
        }
        if (attachmentTexts != null) {
            for (Map.Entry<Long, String> entry : attachmentTexts.entrySet()) {
                sb.append("--- Attachment ").append(entry.getKey()).append(" ---\n");
                sb.append(entry.getValue()).append("\n\n");
            }
        }
        if (additionalContext != null && !additionalContext.isEmpty()) {
            sb.append("--- Additional Context ---\n");
            sb.append(additionalContext).append("\n\n");
        }
        String result = sb.toString();
        if (result.length() > maxLength) {
            return result.substring(0, maxLength) + "\n...[TRUNCATED]";
        }
        return result;
    }
}
