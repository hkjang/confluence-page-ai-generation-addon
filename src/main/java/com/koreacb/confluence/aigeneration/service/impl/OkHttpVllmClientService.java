package com.koreacb.confluence.aigeneration.service.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.koreacb.confluence.aigeneration.model.AdminConfig;
import com.koreacb.confluence.aigeneration.model.VllmRequest;
import com.koreacb.confluence.aigeneration.model.VllmResponse;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.VllmClientService;
import com.koreacb.confluence.aigeneration.security.LogMasker;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Named("vllmClientService")
public class OkHttpVllmClientService implements VllmClientService {
    private static final Logger LOG = LoggerFactory.getLogger(OkHttpVllmClientService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_MS = 1000;

    private final AdminConfigService adminConfigService;
    private final Gson gson;
    private volatile OkHttpClient httpClient;
    private volatile int lastTimeout = 0;

    @Inject
    public OkHttpVllmClientService(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    @Override
    public VllmResponse generateCompletion(VllmRequest request) throws Exception {
        AdminConfig config = adminConfigService.getConfig();
        String url = normalize(config.getVllmEndpoint()) + "/v1/chat/completions";
        String body = gson.toJson(request);

        Exception lastEx = null;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Request.Builder rb = new Request.Builder().url(url)
                        .post(RequestBody.create(JSON_TYPE, body));
                addAuth(rb);

                try (Response resp = getClient().newCall(rb.build()).execute()) {
                    if (!resp.isSuccessful()) {
                        String err = resp.body() != null ? resp.body().string() : "";
                        if (resp.code() >= 500 && i < MAX_RETRIES - 1) {
                            lastEx = new IOException("vLLM " + resp.code());
                            Thread.sleep(RETRY_BASE_MS * (long) Math.pow(2, i));
                            continue;
                        }
                        throw new IOException("vLLM error " + resp.code() + ": " + LogMasker.truncateContent(err));
                    }
                    String respBody = resp.body() != null ? resp.body().string() : null;
                    if (respBody == null) throw new IOException("Empty response");
                    VllmResponse vr = gson.fromJson(respBody, VllmResponse.class);
                    LOG.debug("vLLM OK: tokens={}", vr.getUsage() != null ? vr.getUsage().getTotalTokens() : "?");
                    return vr;
                }
            } catch (IOException e) {
                lastEx = e;
                if (i < MAX_RETRIES - 1) {
                    LOG.warn("vLLM retry {}: {}", i + 1, e.getMessage());
                    Thread.sleep(RETRY_BASE_MS * (long) Math.pow(2, i));
                }
            }
        }
        throw lastEx != null ? lastEx : new IOException("Failed after retries");
    }

    @Override
    public void generateCompletionStreaming(VllmRequest request, StreamCallback callback) throws Exception {
        AdminConfig config = adminConfigService.getConfig();
        request.setStream(true);
        String url = normalize(config.getVllmEndpoint()) + "/v1/chat/completions";

        Request.Builder rb = new Request.Builder().url(url)
                .post(RequestBody.create(JSON_TYPE, gson.toJson(request)));
        addAuth(rb);
        rb.addHeader("Accept", "text/event-stream");

        try (Response resp = getClient().newCall(rb.build()).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                callback.onError(new IOException("vLLM streaming error " + resp.code()));
                return;
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            VllmResponse chunk = gson.fromJson(data, VllmResponse.class);
                            String content = chunk.getContent();
                            if (content != null) { full.append(content); callback.onChunk(content); }
                        } catch (Exception ignored) {}
                    }
                }
            }
            VllmResponse fr = new VllmResponse();
            VllmResponse.Usage u = new VllmResponse.Usage();
            u.setCompletionTokens(full.length() / 4);
            fr.setUsage(u);
            callback.onComplete(fr);
        } catch (Exception e) { callback.onError(e); throw e; }
    }

    @Override
    public boolean testConnection() {
        try {
            String url = normalize(adminConfigService.getConfig().getVllmEndpoint()) + "/v1/models";
            Request.Builder rb = new Request.Builder().url(url).get();
            addAuth(rb);
            try (Response resp = getClient().newCall(rb.build()).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { LOG.warn("vLLM test failed: {}", e.getMessage()); return false; }
    }

    @Override
    public String getAvailableModel() {
        try {
            String url = normalize(adminConfigService.getConfig().getVllmEndpoint()) + "/v1/models";
            Request.Builder rb = new Request.Builder().url(url).get();
            addAuth(rb);
            try (Response resp = getClient().newCall(rb.build()).execute()) {
                return resp.isSuccessful() && resp.body() != null ? resp.body().string() : null;
            }
        } catch (Exception e) { return null; }
    }

    private void addAuth(Request.Builder rb) {
        String key = adminConfigService.getDecryptedApiKey();
        if (key != null && !key.isEmpty()) rb.addHeader("Authorization", "Bearer " + key);
    }

    private OkHttpClient getClient() {
        int t = adminConfigService.getConfig().getTimeoutSeconds();
        if (httpClient == null || t != lastTimeout) {
            synchronized (this) {
                if (httpClient == null || t != lastTimeout) {
                    httpClient = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(t, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                            .build();
                    lastTimeout = t;
                }
            }
        }
        return httpClient;
    }

    private String normalize(String ep) {
        if (ep == null) throw new IllegalStateException("vLLM endpoint not configured");
        return ep.endsWith("/") ? ep.substring(0, ep.length() - 1) : ep;
    }
}
