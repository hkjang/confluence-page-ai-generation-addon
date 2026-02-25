package com.koreacb.confluence.aigeneration.servlet;

import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Admin servlet serving templates for plugin configuration.
 *
 * IMPORTANT: This servlet is declared in atlassian-plugin.xml and instantiated
 * by Confluence's HOST Spring context (DefaultSpringContainerAccessor), NOT by
 * the plugin's own Spring Scanner context. Therefore @ComponentImport, @Inject,
 * and ComponentLocator for SAL services do NOT work here.
 *
 * Instead, we use Confluence's native APIs:
 *   - AuthenticatedUserThreadLocal for current user (always available in Confluence web context)
 *   - PermissionManager via ContainerManager for admin check
 *   - Standard Confluence login URL for redirect
 *
 * Routes:
 *   /config  - General settings (vLLM endpoint, model, API key, defaults)
 *   /prompts - Prompt template management
 *   /policies - Space policy management
 *   /audit   - Audit log viewer
 */
public class AdminConfigServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminConfigServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Use Confluence's thread-local user (set by Confluence's authentication filters)
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) {
            redirectToLogin(req, resp);
            return;
        }

        // Check admin permission using Confluence's PermissionManager
        if (!isConfluenceAdmin(user)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "System admin access required");
            return;
        }

        // Route to appropriate template
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/config";

        String templatePath;
        switch (pathInfo) {
            case "/prompts":
                templatePath = "templates/admin-prompts.vm";
                break;
            case "/policies":
                templatePath = "templates/admin-policies.vm";
                break;
            case "/audit":
                templatePath = "templates/admin-audit.vm";
                break;
            case "/config":
            default:
                templatePath = "templates/admin-config.vm";
                break;
        }

        resp.setContentType("text/html;charset=UTF-8");

        try {
            // Read template from classpath and render with simple variable substitution
            InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath);
            if (is == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Template not found: " + templatePath);
                return;
            }

            StringBuilder sb = new StringBuilder();
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();

            String html = sb.toString();
            html = html.replace("$baseUrl", req.getContextPath());
            html = html.replace("$currentPath", pathInfo);
            html = html.replace("$pluginKey", "com.koreacb.confluence.ai-page-generation");

            PrintWriter writer = resp.getWriter();
            writer.write(html);
            writer.flush();

        } catch (Exception e) {
            LOG.error("Failed to render template: {}", templatePath, e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to render admin page");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // POST requests are handled by REST endpoints
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Use REST API for configuration changes");
    }

    /**
     * Check if the user is a Confluence system administrator.
     * Uses ContainerManager to get PermissionManager from Confluence's host context.
     */
    private boolean isConfluenceAdmin(ConfluenceUser user) {
        try {
            PermissionManager pm = (PermissionManager)
                    com.atlassian.spring.container.ContainerManager.getComponent("permissionManager");
            if (pm != null) {
                return pm.isConfluenceAdministrator(user);
            }
        } catch (Exception e) {
            LOG.warn("Failed to check admin via ContainerManager, trying ContainerContext", e);
        }

        // Fallback: try via ContainerContext
        try {
            Object ctx = com.atlassian.spring.container.ContainerManager.getInstance().getContainerContext();
            if (ctx != null) {
                Object pm = ((com.atlassian.spring.container.ContainerContext) ctx).getComponent("permissionManager");
                if (pm instanceof PermissionManager) {
                    return ((PermissionManager) pm).isConfluenceAdministrator(user);
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to check admin permission via all methods", e);
        }

        return false;
    }

    /**
     * Redirect to Confluence's standard login page.
     * Uses the standard /login.action URL with os_destination for return URL.
     */
    private void redirectToLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String destination = req.getRequestURI();
        String queryString = req.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            destination += "?" + queryString;
        }
        String loginUrl = req.getContextPath() + "/login.action?os_destination="
                + URLEncoder.encode(destination, "UTF-8");
        resp.sendRedirect(loginUrl);
    }
}
