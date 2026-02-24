package com.koreacb.confluence.aigeneration.security;

import javax.inject.Named;
import java.util.regex.Pattern;

@Named("logMasker")
public class LogMasker {
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(Bearer\\s+|api[_-]?key[\"'\\s:=]+)[\\w\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern KOREAN_RRN_PATTERN = Pattern.compile("\\d{6}[\\s-]?\\d{7}");
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    private static final int MAX_CONTENT_LOG_LENGTH = 100;

    public static String mask(String input) {
        if (input == null) return null;
        String m = input;
        m = API_KEY_PATTERN.matcher(m).replaceAll("***API_KEY***");
        m = EMAIL_PATTERN.matcher(m).replaceAll("***EMAIL***");
        m = KOREAN_RRN_PATTERN.matcher(m).replaceAll("***RRN***");
        m = IP_PATTERN.matcher(m).replaceAll("***IP***");
        return m;
    }

    public static String truncateContent(String content) {
        if (content == null) return null;
        if (content.length() <= MAX_CONTENT_LOG_LENGTH) return content;
        return content.substring(0, MAX_CONTENT_LOG_LENGTH) + "...[TRUNCATED]";
    }

    public static String safeJobLog(String jobId, String status, String spaceKey, String templateKey) {
        return String.format("jobId=%s, status=%s, spaceKey=%s, templateKey=%s",
                jobId, status, spaceKey, templateKey);
    }
}
