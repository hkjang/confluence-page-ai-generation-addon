package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.sal.api.component.ComponentLocator;
import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.JobQueueService;
import com.koreacb.confluence.aigeneration.service.VllmClientService;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for health checks and system status.
 * Uses ComponentLocator for service lookups to avoid Spring context isolation issues.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class HealthResource {

    private VllmClientService vllmClient;
    private AdminConfigService adminConfigService;
    private JobQueueService jobQueueService;

    private void init() {
        if (vllmClient != null) return;
        vllmClient = ComponentLocator.getComponent(VllmClientService.class);
        adminConfigService = ComponentLocator.getComponent(AdminConfigService.class);
        jobQueueService = ComponentLocator.getComponent(JobQueueService.class);
    }

    @GET
    public Response getHealth(@Context HttpServletRequest httpReq) {
        init();
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("configured", adminConfigService != null && adminConfigService.isConfigured());
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> queue = new LinkedHashMap<>();
        try {
            if (jobQueueService != null) {
                queue.put("depth", jobQueueService.getQueueDepth());
                queue.put("active", jobQueueService.getActiveJobCount());
            }
        } catch (Exception e) {
            queue.put("error", e.getMessage());
        }
        health.put("queue", queue);

        return Response.ok(health).build();
    }

    @GET
    @Path("/vllm")
    public Response testVllm(@Context HttpServletRequest httpReq) {
        init();
        Map<String, Object> result = new LinkedHashMap<>();

        if (adminConfigService == null || !adminConfigService.isConfigured()) {
            result.put("connected", false);
            result.put("error", "vLLM is not configured");
            return Response.ok(result).build();
        }

        try {
            boolean connected = vllmClient.testConnection();
            result.put("connected", connected);
            result.put("endpoint", adminConfigService.getConfig().getVllmEndpoint());
            result.put("model", adminConfigService.getConfig().getModel());

            if (connected) {
                String models = vllmClient.getAvailableModel();
                if (models != null) {
                    result.put("availableModels", models);
                }
            }
        } catch (Exception e) {
            result.put("connected", false);
            result.put("error", e.getMessage());
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/queue")
    public Response getQueueStatus(@Context HttpServletRequest httpReq) {
        init();
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            if (jobQueueService != null) {
                status.put("depth", jobQueueService.getQueueDepth());
                status.put("active", jobQueueService.getActiveJobCount());
            }
            status.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            status.put("error", e.getMessage());
        }
        return Response.ok(status).build();
    }
}
