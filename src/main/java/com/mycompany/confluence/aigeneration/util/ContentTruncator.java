package com.mycompany.confluence.aigeneration.util;

import javax.inject.Named;

@Named("contentTruncator")
public class ContentTruncator {
    public String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) return content;
        int cutPoint = content.lastIndexOf('\n', maxLength);
        if (cutPoint < maxLength / 2) cutPoint = maxLength;
        return content.substring(0, cutPoint) + "\n...[TRUNCATED]";
    }
}
