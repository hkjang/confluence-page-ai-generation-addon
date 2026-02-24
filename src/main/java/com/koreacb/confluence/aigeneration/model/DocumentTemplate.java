package com.koreacb.confluence.aigeneration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocumentTemplate {
    private String key;
    private String nameI18nKey;
    private String descriptionI18nKey;
    private String iconClass;
    private List<SectionDefinition> requiredSections;
    private List<SectionDefinition> recommendedSections;
    private List<String> qualityChecklist;
    private Map<String, String> examplePhrases;

    public DocumentTemplate() {}

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getNameI18nKey() { return nameI18nKey; }
    public void setNameI18nKey(String nameI18nKey) { this.nameI18nKey = nameI18nKey; }

    public String getDescriptionI18nKey() { return descriptionI18nKey; }
    public void setDescriptionI18nKey(String descriptionI18nKey) { this.descriptionI18nKey = descriptionI18nKey; }

    public String getIconClass() { return iconClass; }
    public void setIconClass(String iconClass) { this.iconClass = iconClass; }

    public List<SectionDefinition> getRequiredSections() { return requiredSections; }
    public void setRequiredSections(List<SectionDefinition> requiredSections) { this.requiredSections = requiredSections; }

    public List<SectionDefinition> getRecommendedSections() { return recommendedSections; }
    public void setRecommendedSections(List<SectionDefinition> recommendedSections) { this.recommendedSections = recommendedSections; }

    public List<String> getQualityChecklist() { return qualityChecklist; }
    public void setQualityChecklist(List<String> qualityChecklist) { this.qualityChecklist = qualityChecklist; }

    public Map<String, String> getExamplePhrases() { return examplePhrases; }
    public void setExamplePhrases(Map<String, String> examplePhrases) { this.examplePhrases = examplePhrases; }

    public List<SectionDefinition> getAllSections() {
        List<SectionDefinition> all = new ArrayList<>();
        if (requiredSections != null) all.addAll(requiredSections);
        if (recommendedSections != null) all.addAll(recommendedSections);
        return all;
    }

    public SectionDefinition getSectionDefinition(String sectionKey) {
        for (SectionDefinition s : getAllSections()) {
            if (s.getKey().equals(sectionKey)) return s;
        }
        return null;
    }
}
