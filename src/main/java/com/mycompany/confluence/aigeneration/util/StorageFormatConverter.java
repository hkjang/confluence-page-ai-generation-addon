package com.mycompany.confluence.aigeneration.util;

import javax.inject.Named;

@Named("storageFormatConverter")
public class StorageFormatConverter {
    public String markdownToStorage(String markdown) {
        if (markdown == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean inList = false;
        for (String line : markdown.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) { if (inList) { sb.append("</ul>\n"); inList = false; } continue; }
            if (t.startsWith("## ")) { close(sb, inList); inList = false; sb.append("<h2>").append(esc(t.substring(3))).append("</h2>\n"); }
            else if (t.startsWith("### ")) { close(sb, inList); inList = false; sb.append("<h3>").append(esc(t.substring(4))).append("</h3>\n"); }
            else if (t.startsWith("- ") || t.startsWith("* ")) { if (!inList) { sb.append("<ul>\n"); inList = true; } sb.append("<li>").append(esc(t.substring(2))).append("</li>\n"); }
            else { close(sb, inList); inList = false; sb.append("<p>").append(esc(t)).append("</p>\n"); }
        }
        if (inList) sb.append("</ul>\n");
        return sb.toString();
    }

    private void close(StringBuilder sb, boolean inList) { if (inList) sb.append("</ul>\n"); }
    private String esc(String t) { return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
}
