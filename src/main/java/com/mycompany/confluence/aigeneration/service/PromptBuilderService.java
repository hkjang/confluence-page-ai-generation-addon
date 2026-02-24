package com.mycompany.confluence.aigeneration.service;

import com.mycompany.confluence.aigeneration.model.PromptContext;
import com.mycompany.confluence.aigeneration.model.SectionDefinition;

public interface PromptBuilderService {
    String buildSystemPrompt(String templateKey, String spaceKey);
    String buildSectionPrompt(SectionDefinition section, PromptContext context,
                              String purpose, String audience, String tone, String lengthPreference);
    String applyGlossary(String prompt, String spaceKey);
    String filterForbiddenWords(String content, String spaceKey);
}
