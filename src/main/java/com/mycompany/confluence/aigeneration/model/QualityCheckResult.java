package com.mycompany.confluence.aigeneration.model;

import java.util.ArrayList;
import java.util.List;

public class QualityCheckResult {
    private List<String> warnings = new ArrayList<>();
    private List<String> missingSections = new ArrayList<>();
    private List<SensitivePatternMatch> sensitivePatterns = new ArrayList<>();
    private List<String> exaggerations = new ArrayList<>();

    public QualityCheckResult() {}

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<String> getMissingSections() { return missingSections; }
    public void setMissingSections(List<String> missingSections) { this.missingSections = missingSections; }

    public List<SensitivePatternMatch> getSensitivePatterns() { return sensitivePatterns; }
    public void setSensitivePatterns(List<SensitivePatternMatch> sensitivePatterns) { this.sensitivePatterns = sensitivePatterns; }

    public List<String> getExaggerations() { return exaggerations; }
    public void setExaggerations(List<String> exaggerations) { this.exaggerations = exaggerations; }

    public boolean hasIssues() {
        return !warnings.isEmpty() || !missingSections.isEmpty()
                || !sensitivePatterns.isEmpty() || !exaggerations.isEmpty();
    }

    public static class SensitivePatternMatch {
        private String patternType;
        private String matchedText;
        private String suggestion;

        public SensitivePatternMatch() {}

        public SensitivePatternMatch(String patternType, String matchedText, String suggestion) {
            this.patternType = patternType;
            this.matchedText = matchedText;
            this.suggestion = suggestion;
        }

        public String getPatternType() { return patternType; }
        public void setPatternType(String patternType) { this.patternType = patternType; }

        public String getMatchedText() { return matchedText; }
        public void setMatchedText(String matchedText) { this.matchedText = matchedText; }

        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }
}
