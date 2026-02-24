package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.GenerationRequest;
import com.koreacb.confluence.aigeneration.model.PromptContext;
import java.util.List;
import java.util.Map;

public interface ContextBuilderService {
    PromptContext buildPromptContext(GenerationRequest request, String userKey);
    Map<Long, String> extractPageContent(List<Long> pageIds, String userKey);
    Map<Long, String> extractAttachmentText(List<Long> attachmentIds, String userKey);
    List<String> extractLabels(String spaceKey);
}
