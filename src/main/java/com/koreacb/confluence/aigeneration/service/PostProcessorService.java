package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import com.koreacb.confluence.aigeneration.model.GenerationResult;
import com.koreacb.confluence.aigeneration.model.QualityCheckResult;

public interface PostProcessorService {
    String convertToStorageFormat(String content);
    QualityCheckResult runQualityChecks(GenerationResult result, DocumentTemplate template);
    String sanitizeContent(String content);
    String maskSensitiveInfo(String content);
}
