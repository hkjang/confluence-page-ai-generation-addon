package com.mycompany.confluence.aigeneration.service;

import com.mycompany.confluence.aigeneration.model.DocumentTemplate;
import com.mycompany.confluence.aigeneration.model.GenerationRequest;
import com.mycompany.confluence.aigeneration.model.GenerationResult;

public interface FallbackService {
    GenerationResult generateFallbackDraft(DocumentTemplate template, GenerationRequest request);
}
