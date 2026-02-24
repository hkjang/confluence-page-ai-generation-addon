package com.mycompany.confluence.aigeneration.model;

public class SectionDefinition {
    private String key;
    private String titleI18nKey;
    private String defaultTitle;
    private String promptHint;
    private boolean required;
    private int orderIndex;

    public SectionDefinition() {}

    public SectionDefinition(String key, String titleI18nKey, String defaultTitle,
                             String promptHint, boolean required, int orderIndex) {
        this.key = key;
        this.titleI18nKey = titleI18nKey;
        this.defaultTitle = defaultTitle;
        this.promptHint = promptHint;
        this.required = required;
        this.orderIndex = orderIndex;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getTitleI18nKey() { return titleI18nKey; }
    public void setTitleI18nKey(String titleI18nKey) { this.titleI18nKey = titleI18nKey; }

    public String getDefaultTitle() { return defaultTitle; }
    public void setDefaultTitle(String defaultTitle) { this.defaultTitle = defaultTitle; }

    public String getPromptHint() { return promptHint; }
    public void setPromptHint(String promptHint) { this.promptHint = promptHint; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
