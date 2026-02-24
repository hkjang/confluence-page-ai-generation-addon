package com.mycompany.confluence.aigeneration.security;

import javax.inject.Named;
import java.util.regex.Pattern;

@Named("contentSanitizer")
public class ContentSanitizer {
    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_TAG = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EVENT_HANDLERS = Pattern.compile(
            "\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_HREF = Pattern.compile(
            "href\\s*=\\s*[\"']\\s*javascript:[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_URI = Pattern.compile(
            "src\\s*=\\s*[\"']\\s*data:[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_TAG = Pattern.compile(
            "<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OBJECT_TAG = Pattern.compile(
            "<object[^>]*>.*?</object>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EMBED_TAG = Pattern.compile(
            "<embed[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_TAG = Pattern.compile(
            "<form[^>]*>.*?</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public String sanitize(String content) {
        if (content == null || content.isEmpty()) return content;
        String s = content;
        s = SCRIPT_TAG.matcher(s).replaceAll("");
        s = STYLE_TAG.matcher(s).replaceAll("");
        s = IFRAME_TAG.matcher(s).replaceAll("");
        s = OBJECT_TAG.matcher(s).replaceAll("");
        s = EMBED_TAG.matcher(s).replaceAll("");
        s = FORM_TAG.matcher(s).replaceAll("");
        s = EVENT_HANDLERS.matcher(s).replaceAll("");
        s = JAVASCRIPT_HREF.matcher(s).replaceAll("href=\"#\"");
        s = DATA_URI.matcher(s).replaceAll("src=\"\"");
        return s;
    }
}
