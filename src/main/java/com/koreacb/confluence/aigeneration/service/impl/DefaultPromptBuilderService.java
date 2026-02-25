package com.koreacb.confluence.aigeneration.service.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.koreacb.confluence.aigeneration.ao.AoForbiddenWord;
import com.koreacb.confluence.aigeneration.ao.AoGlossaryTerm;
import com.koreacb.confluence.aigeneration.ao.AoPromptTemplate;
import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import com.koreacb.confluence.aigeneration.model.PromptContext;
import com.koreacb.confluence.aigeneration.model.SectionDefinition;
import com.koreacb.confluence.aigeneration.service.PromptBuilderService;
import com.koreacb.confluence.aigeneration.service.TemplateRegistryService;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Named("promptBuilderService")
public class DefaultPromptBuilderService implements PromptBuilderService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPromptBuilderService.class);
    private static final int MAX_CTX = 32000;
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private final ActiveObjects ao;
    private final TemplateRegistryService templateRegistryService;

    @Inject
    public DefaultPromptBuilderService(@ComponentImport ActiveObjects ao, TemplateRegistryService templateRegistryService) {
        this.ao = ao;
        this.templateRegistryService = templateRegistryService;
    }

    @Override
    public String buildSystemPrompt(String templateKey, String spaceKey) {
        StringBuilder sb = new StringBuilder();
        AoPromptTemplate[] custom = ao.find(AoPromptTemplate.class,
                Query.select().where("TEMPLATE_KEY = ?", templateKey));
        if (custom.length > 0 && custom[0].isActive() && custom[0].getSystemPrompt() != null) {
            sb.append(custom[0].getSystemPrompt()).append("\n\n");
        } else {
            sb.append("당신은 전문 문서 작성 전문가입니다. 조직 내부에서 사용되는 고품질의 전문 문서를 작성합니다.\n\n");
        }
        DocumentTemplate t = templateRegistryService.getTemplate(templateKey);
        if (t != null) sb.append("문서 유형: ").append(t.getNameI18nKey()).append("\n");
        appendGlossary(sb, spaceKey);
        appendForbidden(sb, spaceKey);
        sb.append("\n출력 형식: Confluence Storage Format (XHTML).\n");
        sb.append("- 섹션 제목은 <h2>, 문단은 <p>, 목록은 <ul><li>, 표는 <table><tbody><tr><th>/<td>를 사용.\n");
        sb.append("- 마크다운 사용 금지. 유효한 XHTML만 출력.\n");
        return sb.toString();
    }

    @Override
    public String buildSectionPrompt(SectionDefinition section, PromptContext context,
                                     String purpose, String audience, String tone, String lengthPref) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 섹션을 작성해 주세요: \"").append(section.getDefaultTitle()).append("\"\n\n");
        sb.append("문서 목적: ").append(purpose).append("\n");
        sb.append("독자: ").append(audienceLabel(audience)).append("\n");
        sb.append("어조: ").append(toneLabel(tone)).append("\n");
        sb.append("길이: ").append(lengthLabel(lengthPref)).append("\n\n");
        if (section.getPromptHint() != null && !section.getPromptHint().isEmpty())
            sb.append("섹션 작성 가이드:\n").append(section.getPromptHint()).append("\n\n");
        if (context != null) {
            String ctx = context.toContextString(MAX_CTX);
            if (!ctx.isEmpty()) sb.append("참고 자료:\n---\n").append(ctx).append("\n---\n\n");
        }
        sb.append("요구사항:\n- 이 섹션은 ").append(section.isRequired() ? "필수" : "권장").append(" 섹션입니다.\n");
        sb.append("- 목표 분량: ").append(wordCount(lengthPref)).append("\n");
        sb.append("- 섹션 제목(<h2>)을 포함하여 출력하세요.\n");
        return sb.toString();
    }

    @Override
    public String applyGlossary(String prompt, String spaceKey) { return prompt; }

    @Override
    public String filterForbiddenWords(String content, String spaceKey) {
        if (content == null) return content;
        String r = content;
        for (AoForbiddenWord w : ao.find(AoForbiddenWord.class, Query.select().where("SPACE_KEY IS NULL")))
            r = applyFW(r, w);
        if (spaceKey != null)
            for (AoForbiddenWord w : ao.find(AoForbiddenWord.class, Query.select().where("SPACE_KEY = ?", spaceKey)))
                r = applyFW(r, w);
        return r;
    }

    private String applyFW(String c, AoForbiddenWord w) {
        try {
            String rep = w.getReplacement() != null ? w.getReplacement() : "";
            if (w.isRegex()) {
                Pattern p = PATTERN_CACHE.computeIfAbsent(w.getPattern(),
                        k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE));
                return p.matcher(c).replaceAll(rep);
            }
            return c.replace(w.getPattern(), rep);
        } catch (Exception e) { LOG.warn("Bad forbidden pattern: {}", w.getPattern()); return c; }
    }

    private void appendGlossary(StringBuilder sb, String spaceKey) {
        AoGlossaryTerm[] g = ao.find(AoGlossaryTerm.class, Query.select().where("SPACE_KEY IS NULL"));
        AoGlossaryTerm[] s = spaceKey != null ? ao.find(AoGlossaryTerm.class, Query.select().where("SPACE_KEY = ?", spaceKey)) : new AoGlossaryTerm[0];
        if (g.length == 0 && s.length == 0) return;
        sb.append("\n용어집:\n");
        for (AoGlossaryTerm t : g) sb.append("- ").append(t.getTerm()).append(": ").append(t.getDefinition()).append("\n");
        for (AoGlossaryTerm t : s) sb.append("- ").append(t.getTerm()).append(": ").append(t.getDefinition()).append("\n");
    }

    private void appendForbidden(StringBuilder sb, String spaceKey) {
        AoForbiddenWord[] g = ao.find(AoForbiddenWord.class, Query.select().where("SPACE_KEY IS NULL"));
        AoForbiddenWord[] s = spaceKey != null ? ao.find(AoForbiddenWord.class, Query.select().where("SPACE_KEY = ?", spaceKey)) : new AoForbiddenWord[0];
        if (g.length == 0 && s.length == 0) return;
        sb.append("\n금칙어:\n");
        for (AoForbiddenWord w : g) { sb.append("- ").append(w.getPattern()); if (w.getReplacement() != null && !w.getReplacement().isEmpty()) sb.append(" → \"").append(w.getReplacement()).append("\""); sb.append("\n"); }
        for (AoForbiddenWord w : s) { sb.append("- ").append(w.getPattern()); if (w.getReplacement() != null && !w.getReplacement().isEmpty()) sb.append(" → \"").append(w.getReplacement()).append("\""); sb.append("\n"); }
    }

    private String audienceLabel(String a) { if(a==null) return "일반"; switch(a){case "team":return "팀 내부";case "management":return "경영진";case "external":return "외부";case "technical":return "기술 전문가";default:return a;} }
    private String toneLabel(String t) { if(t==null) return "전문적"; switch(t){case "formal":return "공식적";case "casual":return "비공식적";case "technical":return "기술적";case "persuasive":return "설득적";default:return t;} }
    private String lengthLabel(String l) { if(l==null) return "표준"; switch(l){case "brief":return "간략";case "standard":return "표준";case "detailed":return "상세";case "comprehensive":return "포괄적";default:return l;} }
    private String wordCount(String l) { if(l==null) return "200-400자"; switch(l){case "brief":return "100-200자";case "standard":return "200-400자";case "detailed":return "400-800자";case "comprehensive":return "800-1500자";default:return "200-400자";} }
}
