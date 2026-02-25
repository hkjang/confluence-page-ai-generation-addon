package com.koreacb.confluence.aigeneration.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.spring.container.ContainerManager;
import com.google.gson.Gson;
import com.koreacb.confluence.aigeneration.ao.*;
import com.koreacb.confluence.aigeneration.model.AdminConfig;
import com.koreacb.confluence.aigeneration.service.*;
import net.java.ao.Query;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * REST resource for admin CRUD operations.
 * Uses ComponentLocator for service lookups to avoid Spring context isolation issues.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Named
public class AdminResource {

    private AdminConfigService adminConfigService;
    private PolicyService policyService;
    private AuditService auditService;
    private UsageTrackingService usageTracking;
    private ActiveObjects ao;
    private final Gson gson = new Gson();

    private void init() {
        if (adminConfigService != null) return;
        adminConfigService = ComponentLocator.getComponent(AdminConfigService.class);
        policyService = ComponentLocator.getComponent(PolicyService.class);
        auditService = ComponentLocator.getComponent(AuditService.class);
        usageTracking = ComponentLocator.getComponent(UsageTrackingService.class);
        ao = ComponentLocator.getComponent(ActiveObjects.class);
    }

    // ─────────────── Config ───────────────

    @GET
    @Path("/config")
    public Response getConfig(@Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        return Response.ok(adminConfigService.getConfig()).build();
    }

    @PUT
    @Path("/config")
    public Response saveConfig(AdminConfig config, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        adminConfigService.saveConfig(config);
        auditAdmin("CONFIG_UPDATED", "Admin config updated");
        return Response.ok(adminConfigService.getConfig()).build();
    }

    @PUT
    @Path("/config/apikey")
    public Response saveApiKey(Map<String, String> body, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return badRequest("apiKey is required");
        }
        adminConfigService.saveApiKey(apiKey);
        auditAdmin("API_KEY_UPDATED", "API key updated");
        Map<String, Boolean> resp = new HashMap<>();
        resp.put("saved", true);
        return Response.ok(resp).build();
    }

    // ─────────────── Prompts ───────────────

    @GET
    @Path("/prompts")
    public Response listPrompts(@Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoPromptTemplate[] prompts = ao.find(AoPromptTemplate.class, Query.select().order("TEMPLATE_KEY ASC"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AoPromptTemplate p : prompts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getID());
            item.put("templateKey", p.getTemplateKey());
            item.put("systemPrompt", p.getSystemPrompt());
            item.put("sectionPrompts", p.getSectionPrompts());
            item.put("active", p.isActive());
            result.add(item);
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("/prompts")
    public Response createPrompt(Map<String, Object> body, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoPromptTemplate p = ao.create(AoPromptTemplate.class);
        p.setTemplateKey((String) body.get("templateKey"));
        p.setSystemPrompt((String) body.get("systemPrompt"));
        p.setSectionPrompts((String) body.get("sectionPrompts"));
        p.setActive(body.get("active") != null && (Boolean) body.get("active"));
        p.save();
        auditAdmin("PROMPT_CREATED", "templateKey=" + body.get("templateKey"));
        return Response.ok(idMap(p.getID())).build();
    }

    @PUT
    @Path("/prompts/{id}")
    public Response updatePrompt(@PathParam("id") int id, Map<String, Object> body,
                                 @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoPromptTemplate p = ao.get(AoPromptTemplate.class, id);
        if (p == null) return notFound("Prompt not found");
        if (body.containsKey("systemPrompt")) p.setSystemPrompt((String) body.get("systemPrompt"));
        if (body.containsKey("sectionPrompts")) p.setSectionPrompts((String) body.get("sectionPrompts"));
        if (body.containsKey("active")) p.setActive((Boolean) body.get("active"));
        p.save();
        auditAdmin("PROMPT_UPDATED", "id=" + id);
        return Response.ok(successMap()).build();
    }

    @DELETE
    @Path("/prompts/{id}")
    public Response deletePrompt(@PathParam("id") int id, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoPromptTemplate p = ao.get(AoPromptTemplate.class, id);
        if (p == null) return notFound("Prompt not found");
        ao.delete(p);
        auditAdmin("PROMPT_DELETED", "id=" + id);
        return Response.ok(successMap()).build();
    }

    // ─────────────── Glossary ───────────────

    @GET
    @Path("/glossary")
    public Response listGlossary(@QueryParam("spaceKey") String spaceKey,
                                 @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoGlossaryTerm[] terms;
        if (spaceKey != null && !spaceKey.isEmpty()) {
            terms = ao.find(AoGlossaryTerm.class, Query.select().where("SPACE_KEY = ?", spaceKey));
        } else {
            terms = ao.find(AoGlossaryTerm.class, Query.select().order("TERM ASC"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AoGlossaryTerm t : terms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getID());
            item.put("term", t.getTerm());
            item.put("definition", t.getDefinition());
            item.put("spaceKey", t.getSpaceKey());
            result.add(item);
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("/glossary")
    public Response createGlossaryTerm(Map<String, String> body, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoGlossaryTerm t = ao.create(AoGlossaryTerm.class);
        t.setTerm(body.get("term"));
        t.setDefinition(body.get("definition"));
        t.setSpaceKey(body.get("spaceKey"));
        t.save();
        auditAdmin("GLOSSARY_CREATED", "term=" + body.get("term"));
        return Response.ok(idMap(t.getID())).build();
    }

    @PUT
    @Path("/glossary/{id}")
    public Response updateGlossaryTerm(@PathParam("id") int id, Map<String, String> body,
                                       @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoGlossaryTerm t = ao.get(AoGlossaryTerm.class, id);
        if (t == null) return notFound("Term not found");
        if (body.containsKey("term")) t.setTerm(body.get("term"));
        if (body.containsKey("definition")) t.setDefinition(body.get("definition"));
        if (body.containsKey("spaceKey")) t.setSpaceKey(body.get("spaceKey"));
        t.save();
        return Response.ok(successMap()).build();
    }

    @DELETE
    @Path("/glossary/{id}")
    public Response deleteGlossaryTerm(@PathParam("id") int id, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoGlossaryTerm t = ao.get(AoGlossaryTerm.class, id);
        if (t == null) return notFound("Term not found");
        ao.delete(t);
        return Response.ok(successMap()).build();
    }

    // ─────────────── Forbidden Words ───────────────

    @GET
    @Path("/forbidden-words")
    public Response listForbiddenWords(@QueryParam("spaceKey") String spaceKey,
                                       @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoForbiddenWord[] words;
        if (spaceKey != null && !spaceKey.isEmpty()) {
            words = ao.find(AoForbiddenWord.class, Query.select().where("SPACE_KEY = ?", spaceKey));
        } else {
            words = ao.find(AoForbiddenWord.class, Query.select().order("PATTERN ASC"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AoForbiddenWord w : words) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", w.getID());
            item.put("pattern", w.getPattern());
            item.put("replacement", w.getReplacement());
            item.put("isRegex", w.isRegex());
            item.put("spaceKey", w.getSpaceKey());
            result.add(item);
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("/forbidden-words")
    public Response createForbiddenWord(Map<String, Object> body, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoForbiddenWord w = ao.create(AoForbiddenWord.class);
        w.setPattern((String) body.get("pattern"));
        w.setReplacement((String) body.get("replacement"));
        w.setRegex(body.get("isRegex") != null && (Boolean) body.get("isRegex"));
        w.setSpaceKey((String) body.get("spaceKey"));
        w.save();
        auditAdmin("FORBIDDEN_CREATED", "pattern=" + body.get("pattern"));
        return Response.ok(idMap(w.getID())).build();
    }

    @DELETE
    @Path("/forbidden-words/{id}")
    public Response deleteForbiddenWord(@PathParam("id") int id, @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoForbiddenWord w = ao.get(AoForbiddenWord.class, id);
        if (w == null) return notFound("Word not found");
        ao.delete(w);
        return Response.ok(successMap()).build();
    }

    // ─────────────── Policies ───────────────

    @GET
    @Path("/policies")
    public Response listPolicies(@Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        List<AoSpacePolicy> policies = policyService.getAllPolicies();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AoSpacePolicy p : policies) {
            result.add(policyToMap(p));
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("/policies/{spaceKey}")
    public Response getPolicy(@PathParam("spaceKey") String spaceKey,
                              @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoSpacePolicy p = policyService.getSpacePolicy(spaceKey);
        if (p == null) return notFound("Policy not found for space: " + spaceKey);
        return Response.ok(policyToMap(p)).build();
    }

    @PUT
    @Path("/policies/{spaceKey}")
    public Response savePolicy(@PathParam("spaceKey") String spaceKey, Map<String, Object> body,
                               @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();

        AoSpacePolicy p = policyService.getSpacePolicy(spaceKey);
        if (p == null) {
            p = ao.create(AoSpacePolicy.class);
            p.setSpaceKey(spaceKey);
        }
        if (body.containsKey("maxRequestsPerDay")) p.setMaxRequestsPerDay(((Number) body.get("maxRequestsPerDay")).intValue());
        if (body.containsKey("maxTokensPerRequest")) p.setMaxTokensPerRequest(((Number) body.get("maxTokensPerRequest")).intValue());
        if (body.containsKey("allowedGroups")) p.setAllowedGroups(gson.toJson(body.get("allowedGroups")));
        if (body.containsKey("allowedTemplates")) p.setAllowedTemplates(gson.toJson(body.get("allowedTemplates")));
        if (body.containsKey("enabled")) p.setEnabled((Boolean) body.get("enabled"));
        p.save();

        auditAdmin("POLICY_SAVED", "spaceKey=" + spaceKey);
        return Response.ok(policyToMap(p)).build();
    }

    @DELETE
    @Path("/policies/{spaceKey}")
    public Response deletePolicy(@PathParam("spaceKey") String spaceKey,
                                 @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();
        AoSpacePolicy p = policyService.getSpacePolicy(spaceKey);
        if (p != null) {
            ao.delete(p);
            auditAdmin("POLICY_DELETED", "spaceKey=" + spaceKey);
        }
        return Response.ok(successMap()).build();
    }

    // ─────────────── Audit ───────────────

    @GET
    @Path("/audit")
    public Response getAuditLog(@QueryParam("userKey") String userKey,
                                @QueryParam("action") String action,
                                @QueryParam("spaceKey") String spaceKey,
                                @QueryParam("offset") @DefaultValue("0") int offset,
                                @QueryParam("limit") @DefaultValue("50") int limit,
                                @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();

        List<AoAuditLog> logs = auditService.queryAuditLog(userKey, spaceKey, action, null, null, offset, limit);
        int total = auditService.getAuditLogCount(userKey, spaceKey, action, null, null);

        List<Map<String, Object>> items = new ArrayList<>();
        for (AoAuditLog log : logs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getID());
            item.put("userKey", log.getUserKey());
            item.put("action", log.getAction());
            item.put("spaceKey", log.getSpaceKey());
            item.put("pageId", log.getPageId());
            item.put("details", log.getDetails());
            item.put("timestamp", log.getCreatedAt() != null ? log.getCreatedAt().getTime() : null);
            item.put("nodeId", log.getNodeId());
            items.add(item);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("items", items);
        resp.put("total", total);
        resp.put("offset", offset);
        resp.put("limit", limit);
        return Response.ok(resp).build();
    }

    // ─────────────── Usage ───────────────

    @GET
    @Path("/usage")
    public Response getUsageSummary(@QueryParam("days") @DefaultValue("7") int days,
                                    @Context HttpServletRequest httpReq) {
        init();
        if (!isAdmin()) return forbidden();

        Calendar cal = Calendar.getInstance();
        Date to = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date from = cal.getTime();

        Map<String, Object> summary = usageTracking.getUsageSummary(null, null, from, to);
        return Response.ok(summary).build();
    }

    // ─────────────── Helpers ───────────────

    private boolean isAdmin() {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) return false;
        try {
            PermissionManager pm = (PermissionManager) ContainerManager.getComponent("permissionManager");
            return pm != null && pm.isConfluenceAdministrator(user);
        } catch (Exception e) { return false; }
    }

    private void auditAdmin(String action, String details) {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user != null && auditService != null) {
            auditService.logAction(user.getName(), action, null, 0, details);
        }
    }

    private Map<String, Object> policyToMap(AoSpacePolicy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spaceKey", p.getSpaceKey());
        m.put("maxRequestsPerDay", p.getMaxRequestsPerDay());
        m.put("maxTokensPerRequest", p.getMaxTokensPerRequest());
        m.put("allowedGroups", p.getAllowedGroups());
        m.put("allowedTemplates", p.getAllowedTemplates());
        m.put("enabled", p.isEnabled());
        return m;
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN).entity(errorMap("Admin access required")).build();
    }
    private Response notFound(String msg) {
        return Response.status(Response.Status.NOT_FOUND).entity(errorMap(msg)).build();
    }
    private Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(errorMap(msg)).build();
    }
    private Map<String, String> errorMap(String msg) {
        Map<String, String> m = new HashMap<>(); m.put("error", msg); return m;
    }
    private Map<String, Object> idMap(int id) {
        Map<String, Object> m = new HashMap<>(); m.put("id", id); m.put("success", true); return m;
    }
    private Map<String, Boolean> successMap() {
        Map<String, Boolean> m = new HashMap<>(); m.put("success", true); return m;
    }
}
