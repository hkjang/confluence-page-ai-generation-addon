package com.mycompany.confluence.aigeneration.service;

import com.mycompany.confluence.aigeneration.model.DocumentTemplate;
import com.mycompany.confluence.aigeneration.model.GenerationResult;
import com.mycompany.confluence.aigeneration.model.QualityCheckResult;

public interface PostProcessorService {
    String convertToStorageFormat(String content);
    QualityCheckResult runQualityChecks(GenerationResult result, DocumentTemplate template);
    String sanitizeContent(String content);
    String maskSensitiveInfo(String content);
}
