package com.koreacb.confluence.aigeneration.service.impl;

import com.koreacb.confluence.aigeneration.model.*;
import com.koreacb.confluence.aigeneration.service.FallbackService;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

@ExportAsService({FallbackService.class})
@Named("fallbackService")
public class DefaultFallbackService implements FallbackService {
    @Override
    public GenerationResult generateFallbackDraft(DocumentTemplate template, GenerationRequest request) {
        GenerationResult r = new GenerationResult();
        r.setStatus(JobStatus.PARTIAL);
        List<GenerationSection> sections = new ArrayList<>();
        for (SectionDefinition sd : template.getAllSections()) {
            GenerationSection s = new GenerationSection();
            s.setKey(sd.getKey()); s.setTitle(sd.getDefaultTitle()); s.setStatus("COMPLETED");
            StringBuilder c = new StringBuilder();
            c.append("<h2>").append(esc(sd.getDefaultTitle())).append("</h2>\n");
            c.append("<ac:structured-macro ac:name=\"info\"><ac:rich-text-body>\n");
            c.append("<p>AI 생성 실패. 템플릿 초안으로 대체되었습니다. 아래 가이드를 참고하여 작성해 주세요.</p>\n");
            c.append("</ac:rich-text-body></ac:structured-macro>\n");
            if (sd.getPromptHint() != null) c.append("<p><strong>작성 가이드:</strong> ").append(esc(sd.getPromptHint())).append("</p>\n");
            if (template.getExamplePhrases() != null && template.getExamplePhrases().containsKey(sd.getKey()))
                c.append("<p><em>예시: ").append(esc(template.getExamplePhrases().get(sd.getKey()))).append("</em></p>\n");
            c.append("<p>[이 섹션의 내용을 여기에 작성해 주세요.]</p>\n");
            s.setContent(c.toString());
            sections.add(s);
        }
        r.setSections(sections);
        return r;
    }

    private String esc(String t) { return t == null ? "" : t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
}
