package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.spring.container.ContainerManager;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for context data retrieval.
 * Uses ContainerManager for Confluence service lookups to avoid Spring context isolation issues.
 */
@Path("/context")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class ContextResource {

    private PageManager pageManager;
    private SpaceManager spaceManager;
    private AttachmentManager attachmentManager;
    private PermissionManager permissionManager;

    private void init() {
        if (pageManager != null) return;
        pageManager = (PageManager) ContainerManager.getComponent("pageManager");
        spaceManager = (SpaceManager) ContainerManager.getComponent("spaceManager");
        attachmentManager = (AttachmentManager) ContainerManager.getComponent("attachmentManager");
        permissionManager = (PermissionManager) ContainerManager.getComponent("permissionManager");
    }

    @GET
    @Path("/pages")
    public Response searchPages(@QueryParam("spaceKey") String spaceKey,
                                @QueryParam("query") @DefaultValue("") String query,
                                @QueryParam("limit") @DefaultValue("20") int limit,
                                @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        if (spaceKey == null || spaceKey.isEmpty()) {
            return badRequest("spaceKey is required");
        }

        Space space = spaceManager.getSpace(spaceKey);
        if (space == null) return notFound("Space not found");

        List<Page> pages = pageManager.getPages(space, true);
        List<Map<String, Object>> result = new ArrayList<>();

        int count = 0;
        for (Page page : pages) {
            if (count >= limit) break;
            if (!query.isEmpty() && !page.getTitle().toLowerCase().contains(query.toLowerCase())) continue;
            if (!permissionManager.hasPermission(user, Permission.VIEW, page)) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", page.getId());
            item.put("title", page.getTitle());
            item.put("spaceKey", spaceKey);
            item.put("lastModified", page.getLastModificationDate() != null ?
                    page.getLastModificationDate().getTime() : null);
            result.add(item);
            count++;
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/attachments")
    public Response getAttachments(@QueryParam("pageId") long pageId,
                                   @QueryParam("limit") @DefaultValue("20") int limit,
                                   @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        Page page = pageManager.getPage(pageId);
        if (page == null) return notFound("Page not found");
        if (!permissionManager.hasPermission(user, Permission.VIEW, page)) {
            return forbidden("No permission to view page");
        }

        List<Attachment> attachments = attachmentManager.getLatestVersionsOfAttachments(page);
        List<Map<String, Object>> result = new ArrayList<>();

        int count = 0;
        for (Attachment att : attachments) {
            if (count >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", att.getId());
            item.put("fileName", att.getFileName());
            item.put("fileSize", att.getFileSize());
            item.put("mediaType", att.getMediaType());
            item.put("pageId", pageId);
            item.put("pageTitle", page.getTitle());
            result.add(item);
            count++;
        }

        return Response.ok(result).build();
    }

    @GET
    @Path("/labels")
    public Response getLabels(@QueryParam("spaceKey") String spaceKey,
                              @QueryParam("query") @DefaultValue("") String query,
                              @QueryParam("limit") @DefaultValue("50") int limit,
                              @Context HttpServletRequest httpReq) {
        init();
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return unauthorized();

        List<Map<String, String>> result = new ArrayList<>();

        if (spaceKey != null && !spaceKey.isEmpty()) {
            Space space = spaceManager.getSpace(spaceKey);
            if (space == null) return notFound("Space not found");

            Set<String> seenLabels = new HashSet<>();
            List<Page> pages = pageManager.getPages(space, true);
            int count = 0;
            for (Page page : pages) {
                if (count >= limit) break;
                List<Label> pageLabels = page.getLabels();
                for (Label label : pageLabels) {
                    if (count >= limit) break;
                    if (seenLabels.contains(label.getName())) continue;
                    if (!query.isEmpty() && !label.getName().toLowerCase().contains(query.toLowerCase())) continue;
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", label.getName());
                    item.put("namespace", label.getNamespace() != null ? label.getNamespace().getPrefix() : "global");
                    result.add(item);
                    seenLabels.add(label.getName());
                    count++;
                }
            }
        }

        return Response.ok(result).build();
    }

    // ─────────────── Helpers ───────────────

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorMap("Authentication required")).build();
    }

    private Response forbidden(String msg) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(errorMap(msg)).build();
    }

    private Response notFound(String msg) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorMap(msg)).build();
    }

    private Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorMap(msg)).build();
    }

    private Map<String, String> errorMap(String msg) {
        Map<String, String> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }
}
