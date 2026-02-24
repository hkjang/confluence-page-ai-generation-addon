package com.koreacb.confluence.aigeneration.model;

import java.util.ArrayList;
import java.util.List;

public class VllmRequest {
    private String model;
    private List<Message> messages;
    private double temperature;
    private int maxTokens;
    private boolean stream;

    public VllmRequest() {
        this.messages = new ArrayList<>();
    }

    public VllmRequest(String model, String systemPrompt, String userPrompt,
                       double temperature, int maxTokens) {
        this.model = model;
        this.messages = new ArrayList<>();
        this.messages.add(new Message("system", systemPrompt));
        this.messages.add(new Message("user", userPrompt));
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = false;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }

    public static class Message {
        private String role;
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
