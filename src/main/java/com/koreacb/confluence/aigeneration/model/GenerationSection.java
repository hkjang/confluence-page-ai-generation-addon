package com.koreacb.confluence.aigeneration.model;

public class GenerationSection {
    private String key;
    private String title;
    private String content;
    private String status;

    public GenerationSection() {}

    public GenerationSection(String key, String title, String content, String status) {
        this.key = key;
        this.title = title;
        this.content = content;
        this.status = status;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
