package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import com.koreacb.confluence.aigeneration.model.GenerationRequest;
import com.koreacb.confluence.aigeneration.model.GenerationResult;

public interface FallbackService {
    GenerationResult generateFallbackDraft(DocumentTemplate template, GenerationRequest request);
}
