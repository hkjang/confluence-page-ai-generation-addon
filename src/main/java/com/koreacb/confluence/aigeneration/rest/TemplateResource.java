package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.koreacb.confluence.aigeneration.model.DocumentTemplate;
import com.koreacb.confluence.aigeneration.service.TemplateRegistryService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for document template operations.
 * Provides template listing and details.
 */
@Path("/templates")
@Produces(MediaType.APPLICATION_JSON)
@Named
public class TemplateResource {

    private final TemplateRegistryService templateService;
    private final UserManager userManager;

    @Inject
    public TemplateResource(TemplateRegistryService templateService, UserManager userManager) {
        this.templateService = templateService;
        this.userManager = userManager;
    }

    /**
     * List all available templates, optionally filtered by space.
     */
    @GET
    public Response listTemplates(@QueryParam("spaceKey") String spaceKey,
                                  @Context HttpServletRequest httpReq) {
        UserProfile user = userManager.getRemoteUser(httpReq);
        if (user == null) return unauthorized();

        List<DocumentTemplate> templates;
        if (spaceKey != null && !spaceKey.isEmpty()) {
            templates = templateService.listTemplatesForSpace(spaceKey);
        } else {
            templates = templateService.listTemplates();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentTemplate t : templates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", t.getKey());
            item.put("nameI18nKey", t.getNameI18nKey());
            item.put("descriptionI18nKey", t.getDescriptionI18nKey());
            item.put("iconClass", t.getIconClass());
            item.put("requiredSectionCount", t.getRequiredSections() != null ? t.getRequiredSections().size() : 0);
            item.put("recommendedSectionCount", t.getRecommendedSections() != null ? t.getRecommendedSections().size() : 0);
            item.put("totalSectionCount", t.getAllSections().size());
            result.add(item);
        }

        return Response.ok(result).build();
    }

    /**
     * Get detailed template information including sections.
     */
    @GET
    @Path("/{templateKey}")
    public Response getTemplate(@PathParam("templateKey") String templateKey,
                                @Context HttpServletRequest httpReq) {
        UserProfile user = userManager.getRemoteUser(httpReq);
        if (user == null) return unauthorized();

        DocumentTemplate template = templateService.getTemplate(templateKey);
        if (template == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorMap("Template not found: " + templateKey)).build();
        }

        return Response.ok(template).build();
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorMap("Authentication required")).build();
    }

    private Map<String, String> errorMap(String msg) {
        Map<String, String> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }
}
