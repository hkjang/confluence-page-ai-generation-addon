package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.sal.api.component.ComponentLocator;
import com.koreacb.confluence.aigeneration.model.*;
import com.koreacb.confluence.aigeneration.service.AiGenerationService;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for AI generation operations.
 * Uses ComponentLocator for service lookups to avoid Spring context isolation issues.
 */
@Path("/generation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Named
public class GenerationResource {

    private AiGenerationService generationService;

    private void init() {
        if (generationService != null) return;
        generationService = ComponentLocator.getComponent(AiGenerationService.class);
    }

    @POST
    @Path("/start")
    public Response startGeneration(GenerationRequest request, @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        try {
            String jobId = generationService.submitGenerationJob(request, user.getName());
            Map<String, String> result = new HashMap<>();
            result.put("jobId", jobId);
            result.put("status", "QUEUED");
            return Response.ok(result).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(errorMap(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorMap(e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(429)
                    .entity(errorMap(e.getMessage())).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/status/{jobId}")
    public Response getStatus(@PathParam("jobId") String jobId, @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        JobStatus status = generationService.getJobStatus(jobId);
        if (status == null) return notFound("Job not found");

        Map<String, Object> result = new HashMap<>();
        result.put("jobId", jobId);
        result.put("status", status.name());
        result.put("progress", generationService.getJobProgress(jobId));
        return Response.ok(result).build();
    }

    @GET
    @Path("/progress/{jobId}")
    public Response getProgress(@PathParam("jobId") String jobId, @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        double progress = generationService.getJobProgress(jobId);
        JobStatus status = generationService.getJobStatus(jobId);
        if (status == null) return notFound("Job not found");

        Map<String, Object> result = new HashMap<>();
        result.put("jobId", jobId);
        result.put("progress", progress);
        result.put("status", status.name());
        result.put("percentComplete", (int) (progress * 100));
        return Response.ok(result).build();
    }

    @GET
    @Path("/result/{jobId}")
    public Response getResult(@PathParam("jobId") String jobId, @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        GenerationResult result = generationService.getJobResult(jobId);
        if (result == null) return notFound("Job not found");

        return Response.ok(result).build();
    }

    @POST
    @Path("/cancel/{jobId}")
    public Response cancelJob(@PathParam("jobId") String jobId, @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        try {
            generationService.cancelJob(jobId, user.getName());
            Map<String, String> result = new HashMap<>();
            result.put("jobId", jobId);
            result.put("status", "CANCELLED");
            return Response.ok(result).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(errorMap(e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @POST
    @Path("/retry/{jobId}")
    public Response retryJob(@PathParam("jobId") String jobId,
                             @QueryParam("sections") String sections,
                             @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        try {
            List<String> sectionKeys = null;
            if (sections != null && !sections.isEmpty()) {
                sectionKeys = Arrays.asList(sections.split(","));
            }

            String newJobId = generationService.retryJob(
                    jobId, user.getName(), sectionKeys);

            Map<String, String> result = new HashMap<>();
            result.put("jobId", newJobId);
            result.put("originalJobId", jobId);
            result.put("status", "QUEUED");
            return Response.ok(result).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(errorMap(e.getMessage())).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    // ─────────────── Helpers ───────────────
    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorMap("Authentication required")).build();
    }

    private Response notFound(String msg) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorMap(msg)).build();
    }

    private Response serverError(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorMap("Internal error: " + e.getMessage())).build();
    }

    private Map<String, String> errorMap(String msg) {
        Map<String, String> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }
}
