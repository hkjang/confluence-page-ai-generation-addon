package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.koreacb.confluence.aigeneration.model.GenerationResult;
import com.koreacb.confluence.aigeneration.model.GenerationSection;
import com.koreacb.confluence.aigeneration.security.ContentSanitizer;
import com.koreacb.confluence.aigeneration.service.AiGenerationService;
import com.koreacb.confluence.aigeneration.service.AuditService;
import com.koreacb.confluence.aigeneration.service.PostProcessorService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for preview and page saving operations.
 * Handles: save as new page, insert into existing page, update section content.
 */
@Path("/preview")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Named
public class PreviewResource {

    private final AiGenerationService generationService;
    private final PageManager pageManager;
    private final SpaceManager spaceManager;
    private final PermissionManager permissionManager;
    private final UserAccessor userAccessor;
    private final UserManager userManager;
    private final ContentSanitizer sanitizer;
    private final PostProcessorService postProcessor;
    private final AuditService auditService;

    @Inject
    public PreviewResource(AiGenerationService generationService, @ComponentImport PageManager pageManager,
                           @ComponentImport SpaceManager spaceManager, @ComponentImport PermissionManager permissionManager,
                           @ComponentImport UserAccessor userAccessor, @ComponentImport UserManager userManager,
                           ContentSanitizer sanitizer, PostProcessorService postProcessor,
                           AuditService auditService) {
        this.generationService = generationService;
        this.pageManager = pageManager;
        this.spaceManager = spaceManager;
        this.permissionManager = permissionManager;
        this.userAccessor = userAccessor;
        this.userManager = userManager;
        this.sanitizer = sanitizer;
        this.postProcessor = postProcessor;
        this.auditService = auditService;
    }

    /**
     * Save generated content as a new page.
     */
    @POST
    @Path("/save")
    public Response saveAsNewPage(SavePageRequest request, @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        String userKey = userProfile.getUserKey().getStringValue();
        ConfluenceUser user = resolveUser(userProfile);
        if (user == null) return unauthorized();

        try {
            // Validate request
            if (request.jobId == null) return badRequest("jobId is required");
            if (request.title == null || request.title.trim().isEmpty()) return badRequest("title is required");
            if (request.spaceKey == null) return badRequest("spaceKey is required");

            // Verify ownership
            String owner = generationService.getJobOwner(request.jobId);
            if (!userKey.equals(owner)) {
                return forbidden("Not the owner of this job");
            }

            // Get space
            Space space = spaceManager.getSpace(request.spaceKey);
            if (space == null) return notFound("Space not found");

            // Check create permission
            if (!permissionManager.hasCreatePermission(user, space, Page.class)) {
                return forbidden("No permission to create pages in this space");
            }

            // Get generation result
            GenerationResult result = generationService.getJobResult(request.jobId);
            if (result == null) return notFound("Job not found");

            // Build page content from sections
            StringBuilder content = new StringBuilder();
            List<GenerationSection> sections = request.sections != null ? request.sections : result.getSections();
            for (GenerationSection section : sections) {
                if (section.getContent() != null) {
                    content.append(sanitizer.sanitize(section.getContent()));
                    content.append("\n");
                }
            }

            // Create page
            Page newPage = new Page();
            newPage.setTitle(request.title.trim());
            newPage.setSpace(space);
            newPage.setBodyAsString(content.toString());
            newPage.setCreator(user);

            // Set parent page if specified
            if (request.parentPageId > 0) {
                Page parent = pageManager.getPage(request.parentPageId);
                if (parent != null && permissionManager.hasPermission(user, Permission.VIEW, parent)) {
                    newPage.setParentPage(parent);
                }
            }

            pageManager.saveContentEntity(newPage, null);

            // Audit
            auditService.logAction(userKey, "PAGE_SAVED", request.spaceKey,
                    newPage.getId(), "jobId=" + request.jobId + ",title=" + request.title);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("pageId", newPage.getId());
            resp.put("pageUrl", "/pages/viewpage.action?pageId=" + newPage.getId());
            resp.put("title", newPage.getTitle());
            return Response.ok(resp).build();

        } catch (Exception e) {
            return serverError(e);
        }
    }

    /**
     * Insert generated content into an existing page (append).
     */
    @POST
    @Path("/insert")
    public Response insertIntoPage(InsertRequest request, @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        String userKey = userProfile.getUserKey().getStringValue();
        ConfluenceUser user = resolveUser(userProfile);

        try {
            if (request.jobId == null) return badRequest("jobId is required");
            if (request.pageId <= 0) return badRequest("pageId is required");

            Page page = pageManager.getPage(request.pageId);
            if (page == null) return notFound("Page not found");

            if (!permissionManager.hasPermission(user, Permission.EDIT, page)) {
                return forbidden("No permission to edit this page");
            }

            GenerationResult result = generationService.getJobResult(request.jobId);
            if (result == null) return notFound("Job not found");

            // Build new content
            StringBuilder newContent = new StringBuilder();
            if (page.getBodyAsString() != null) {
                newContent.append(page.getBodyAsString()).append("\n");
            }

            List<GenerationSection> sections = request.sections != null ? request.sections : result.getSections();
            for (GenerationSection section : sections) {
                if (section.getContent() != null) {
                    newContent.append(sanitizer.sanitize(section.getContent()));
                    newContent.append("\n");
                }
            }

            page.setBodyAsString(newContent.toString());
            pageManager.saveContentEntity(page, null);

            auditService.logAction(userKey, "PAGE_INSERTED", page.getSpaceKey(),
                    page.getId(), "jobId=" + request.jobId);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("pageId", page.getId());
            resp.put("pageUrl", "/pages/viewpage.action?pageId=" + page.getId());
            resp.put("title", page.getTitle());
            return Response.ok(resp).build();

        } catch (Exception e) {
            return serverError(e);
        }
    }

    /**
     * Update a single section's content (for in-preview editing).
     */
    @PUT
    @Path("/section")
    public Response updateSection(UpdateSectionRequest request, @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        try {
            if (request.content == null) return badRequest("content is required");

            // Sanitize the updated content
            String sanitized = sanitizer.sanitize(request.content);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sectionKey", request.sectionKey);
            resp.put("content", sanitized);
            resp.put("sanitized", true);
            return Response.ok(resp).build();

        } catch (Exception e) {
            return serverError(e);
        }
    }

    // ─────────────── Request DTOs ───────────────
    public static class SavePageRequest {
        public String jobId;
        public String title;
        public String spaceKey;
        public long parentPageId;
        public List<GenerationSection> sections;
    }

    public static class InsertRequest {
        public String jobId;
        public long pageId;
        public List<GenerationSection> sections;
    }

    public static class UpdateSectionRequest {
        public String jobId;
        public String sectionKey;
        public String content;
    }

    // ─────────────── Helpers ───────────────

    private ConfluenceUser resolveUser(UserProfile userProfile) {
        if (userProfile == null || userProfile.getUsername() == null) return null;
        return userAccessor.getUserByName(userProfile.getUsername());
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED).entity(errorMap("Authentication required")).build();
    }
    private Response forbidden(String msg) {
        return Response.status(Response.Status.FORBIDDEN).entity(errorMap(msg)).build();
    }
    private Response notFound(String msg) {
        return Response.status(Response.Status.NOT_FOUND).entity(errorMap(msg)).build();
    }
    private Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(errorMap(msg)).build();
    }
    private Response serverError(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorMap(e.getMessage())).build();
    }
    private Map<String, String> errorMap(String msg) {
        Map<String, String> m = new HashMap<>(); m.put("error", msg); return m;
    }
}
