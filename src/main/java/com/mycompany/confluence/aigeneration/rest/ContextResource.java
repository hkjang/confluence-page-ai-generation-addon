package com.mycompany.confluence.aigeneration.rest;

import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.labels.LabelManager;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for context data retrieval.
 * Provides pages, attachments, and labels filtered by user permissions.
 */
@Path("/context")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class ContextResource {

    private final PageManager pageManager;
    private final SpaceManager spaceManager;
    private final AttachmentManager attachmentManager;
    private final LabelManager labelManager;
    private final PermissionManager permissionManager;
    private final UserAccessor userAccessor;
    private final UserManager userManager;

    @Inject
    public ContextResource(PageManager pageManager, SpaceManager spaceManager,
                           AttachmentManager attachmentManager, LabelManager labelManager,
                           PermissionManager permissionManager, UserAccessor userAccessor,
                           UserManager userManager) {
        this.pageManager = pageManager;
        this.spaceManager = spaceManager;
        this.attachmentManager = attachmentManager;
        this.labelManager = labelManager;
        this.permissionManager = permissionManager;
        this.userAccessor = userAccessor;
        this.userManager = userManager;
    }

    /**
     * Search pages in a space that the user can view.
     */
    @GET
    @Path("/pages")
    public Response searchPages(@QueryParam("spaceKey") String spaceKey,
                                @QueryParam("query") @DefaultValue("") String query,
                                @QueryParam("limit") @DefaultValue("20") int limit,
                                @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        if (spaceKey == null || spaceKey.isEmpty()) {
            return badRequest("spaceKey is required");
        }

        ConfluenceUser user = resolveUser(userProfile);
        if (user == null) return unauthorized();

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

    /**
     * Get attachments for a page that the user can view.
     */
    @GET
    @Path("/attachments")
    public Response getAttachments(@QueryParam("pageId") long pageId,
                                   @QueryParam("limit") @DefaultValue("20") int limit,
                                   @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        ConfluenceUser user = resolveUser(userProfile);

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

    /**
     * Get labels in a space.
     */
    @GET
    @Path("/labels")
    public Response getLabels(@QueryParam("spaceKey") String spaceKey,
                              @QueryParam("query") @DefaultValue("") String query,
                              @QueryParam("limit") @DefaultValue("50") int limit,
                              @Context HttpServletRequest httpReq) {
        UserProfile userProfile = userManager.getRemoteUser(httpReq);
        if (userProfile == null) return unauthorized();

        List<Map<String, String>> result = new ArrayList<>();

        if (spaceKey != null && !spaceKey.isEmpty()) {
            Space space = spaceManager.getSpace(spaceKey);
            if (space == null) return notFound("Space not found");

            // Collect unique labels from pages in the space
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

    private ConfluenceUser resolveUser(UserProfile userProfile) {
        if (userProfile == null || userProfile.getUsername() == null) return null;
        return userAccessor.getUserByName(userProfile.getUsername());
    }

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
