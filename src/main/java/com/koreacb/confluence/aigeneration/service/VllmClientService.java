package com.koreacb.confluence.aigeneration.service;

import com.koreacb.confluence.aigeneration.model.VllmRequest;
import com.koreacb.confluence.aigeneration.model.VllmResponse;

public interface VllmClientService {
    VllmResponse generateCompletion(VllmRequest request) throws Exception;
    void generateCompletionStreaming(VllmRequest request, StreamCallback callback) throws Exception;
    boolean testConnection();
    String getAvailableModel();

    interface StreamCallback {
        void onChunk(String content);
        void onComplete(VllmResponse fullResponse);
        void onError(Exception e);
    }
}
