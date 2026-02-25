package com.koreacb.confluence.aigeneration.service.impl;

import com.google.gson.Gson;
import com.koreacb.confluence.aigeneration.ao.AoSpacePolicy;
import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import com.koreacb.confluence.aigeneration.service.PolicyService;
import com.koreacb.confluence.aigeneration.service.TemplateRegistryService;
import com.koreacb.confluence.aigeneration.template.BuiltInTemplates;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@ExportAsService({TemplateRegistryService.class})
@Named("templateRegistryService")
public class DefaultTemplateRegistryService implements TemplateRegistryService {
    private static final Gson GSON = new Gson();
    private final PolicyService policyService;
    private final List<DocumentTemplate> builtIn;

    @Inject
    public DefaultTemplateRegistryService(PolicyService policyService) {
        this.policyService = policyService;
        this.builtIn = BuiltInTemplates.getAll();
    }

    @Override
    public DocumentTemplate getTemplate(String key) {
        for (DocumentTemplate t : builtIn) if (t.getKey().equals(key)) return t;
        return null;
    }

    @Override
    public List<DocumentTemplate> listTemplates() { return new ArrayList<>(builtIn); }

    @Override
    public List<DocumentTemplate> listTemplatesForSpace(String spaceKey) {
        List<DocumentTemplate> all = listTemplates();
        if (spaceKey == null) return all;
        AoSpacePolicy p = policyService.getSpacePolicy(spaceKey);
        if (p == null || p.getAllowedTemplates() == null || p.getAllowedTemplates().isEmpty()) return all;
        try {
            String[] keys = GSON.fromJson(p.getAllowedTemplates(), String[].class);
            if (keys == null || keys.length == 0) return all;
            Set<String> allowed = new HashSet<>(Arrays.asList(keys));
            List<DocumentTemplate> filtered = new ArrayList<>();
            for (DocumentTemplate t : all) if (allowed.contains(t.getKey())) filtered.add(t);
            return filtered;
        } catch (Exception e) { return all; }
    }
}
