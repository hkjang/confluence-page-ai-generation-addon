package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import java.util.List;

public interface TemplateRegistryService {
    DocumentTemplate getTemplate(String key);
    List<DocumentTemplate> listTemplates();
    List<DocumentTemplate> listTemplatesForSpace(String spaceKey);
}
