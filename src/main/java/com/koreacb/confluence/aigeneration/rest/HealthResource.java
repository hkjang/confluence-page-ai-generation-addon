package com.koreacb.confluence.aigeneration.rest;

import com.koreacb.confluence.aigeneration.service.AdminConfigService;
import com.koreacb.confluence.aigeneration.service.JobQueueService;
import com.koreacb.confluence.aigeneration.service.VllmClientService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for health checks and system status.
 * Provides: vLLM connection test, plugin status, queue status.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class HealthResource {

    private final VllmClientService vllmClient;
    private final AdminConfigService adminConfigService;
    private final JobQueueService jobQueueService;

    @Inject
    public HealthResource(VllmClientService vllmClient, AdminConfigService adminConfigService,
                          JobQueueService jobQueueService) {
        this.vllmClient = vllmClient;
        this.adminConfigService = adminConfigService;
        this.jobQueueService = jobQueueService;
    }

    /**
     * Plugin overall health status.
     */
    @GET
    public Response getHealth(@Context HttpServletRequest httpReq) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("configured", adminConfigService.isConfigured());
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> queue = new LinkedHashMap<>();
        try {
            queue.put("depth", jobQueueService.getQueueDepth());
            queue.put("active", jobQueueService.getActiveJobCount());
        } catch (Exception e) {
            queue.put("error", e.getMessage());
        }
        health.put("queue", queue);

        return Response.ok(health).build();
    }

    /**
     * Test vLLM connection.
     */
    @GET
    @Path("/vllm")
    public Response testVllm(@Context HttpServletRequest httpReq) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!adminConfigService.isConfigured()) {
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

    /**
     * Queue status for monitoring.
     */
    @GET
    @Path("/queue")
    public Response getQueueStatus(@Context HttpServletRequest httpReq) {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            status.put("depth", jobQueueService.getQueueDepth());
            status.put("active", jobQueueService.getActiveJobCount());
            status.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            status.put("error", e.getMessage());
        }
        return Response.ok(status).build();
    }
}
