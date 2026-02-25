package com.koreacb.confluence.aigeneration.service.impl;

import com.koreacb.confluence.aigeneration.model.*;
import com.koreacb.confluence.aigeneration.security.ContentSanitizer;
import com.koreacb.confluence.aigeneration.service.PostProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ExportAsService({PostProcessorService.class})
@Named("postProcessorService")
public class DefaultPostProcessorService implements PostProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPostProcessorService.class);
    private static final Pattern EMAIL_P = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern RRN_P = Pattern.compile("\\d{6}[\\s-]?\\d{7}");
    private static final Pattern APIKEY_P = Pattern.compile("(?i)(api[_-]?key|secret|password|token)[\\s:=\"']+[\\w\\-]{8,}");
    private static final Pattern IP_P = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final String[] EXAG = {"최고의","완벽한","절대로","반드시","확실히","무조건","세계 최초","업계 최고","혁신적인","획기적인"};
    private static final String[] UNCERT = {"아마도","인 것 같습니다","일 수도 있습니다","추정됩니다","확인이 필요합니다"};

    private final ContentSanitizer sanitizer;

    @Inject
    public DefaultPostProcessorService(ContentSanitizer sanitizer) { this.sanitizer = sanitizer; }

    @Override
    public String convertToStorageFormat(String content) {
        if (content == null) return content;
        String p = content.trim();
        if (!p.startsWith("<")) p = mdToXhtml(p);
        return fixTags(p);
    }

    @Override
    public QualityCheckResult runQualityChecks(GenerationResult result, DocumentTemplate template) {
        QualityCheckResult qc = new QualityCheckResult();
        if (template == null || result == null || result.getSections() == null) return qc;
        Set<String> done = new HashSet<>();
        for (GenerationSection s : result.getSections()) {
            if ("COMPLETED".equals(s.getStatus())) done.add(s.getKey());
            if (s.getContent() != null) { checkSensitive(s.getContent(), qc); checkExag(s.getContent(), qc); }
        }
        if (template.getRequiredSections() != null)
            for (SectionDefinition req : template.getRequiredSections())
                if (!done.contains(req.getKey())) { qc.getMissingSections().add(req.getDefaultTitle()); qc.getWarnings().add("필수 섹션 누락: " + req.getDefaultTitle()); }
        return qc;
    }

    @Override public String sanitizeContent(String c) { return sanitizer.sanitize(c); }

    @Override
    public String maskSensitiveInfo(String c) {
        if (c == null) return null;
        String m = c;
        m = EMAIL_P.matcher(m).replaceAll("[이메일]");
        m = RRN_P.matcher(m).replaceAll("[주민번호]");
        m = APIKEY_P.matcher(m).replaceAll("[민감정보]");
        return m;
    }

    private void checkSensitive(String c, QualityCheckResult qc) {
        check(c, EMAIL_P, "이메일", qc); check(c, RRN_P, "주민등록번호", qc);
        check(c, APIKEY_P, "API키/비밀값", qc); check(c, IP_P, "IP주소", qc);
    }

    private void check(String c, Pattern p, String type, QualityCheckResult qc) {
        Matcher m = p.matcher(c);
        while (m.find()) {
            String t = m.group();
            String masked = t.length() > 4 ? t.substring(0,2) + "***" + t.substring(t.length()-2) : "***";
            qc.getSensitivePatterns().add(new QualityCheckResult.SensitivePatternMatch(type, masked, type + " 정보 포함. 확인 후 제거 필요."));
        }
    }

    private void checkExag(String c, QualityCheckResult qc) {
        for (String e : EXAG) if (c.contains(e)) qc.getExaggerations().add("과장 표현: \"" + e + "\"");
        for (String u : UNCERT) if (c.contains(u)) qc.getWarnings().add("불확실 표현: \"" + u + "\"");
    }

    private String mdToXhtml(String md) {
        StringBuilder x = new StringBuilder();
        boolean inList = false;
        for (String line : md.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) { if (inList) { x.append("</ul>\n"); inList = false; } continue; }
            if (t.startsWith("## ")) { if (inList) { x.append("</ul>\n"); inList = false; } x.append("<h2>").append(esc(t.substring(3))).append("</h2>\n"); }
            else if (t.startsWith("### ")) { if (inList) { x.append("</ul>\n"); inList = false; } x.append("<h3>").append(esc(t.substring(4))).append("</h3>\n"); }
            else if (t.startsWith("- ") || t.startsWith("* ")) { if (!inList) { x.append("<ul>\n"); inList = true; } x.append("<li>").append(esc(t.substring(2))).append("</li>\n"); }
            else { if (inList) { x.append("</ul>\n"); inList = false; } x.append("<p>").append(esc(t)).append("</p>\n"); }
        }
        if (inList) x.append("</ul>\n");
        return x.toString();
    }

    private String fixTags(String x) {
        return x.replaceAll("<(br|hr|img|input)([^/]*?)(?<!/)>", "<$1$2/>");
    }

    private String esc(String t) {
        return t == null ? "" : t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}
