package com.koreacb.confluence.aigeneration.servlet;

import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Admin servlet serving Velocity templates for plugin configuration.
 * Routes:
 *   /config  - General settings (vLLM endpoint, model, API key, defaults)
 *   /prompts - Prompt template management
 *   /policies - Space policy management
 *   /audit   - Audit log viewer
 */
@Named
public class AdminConfigServlet extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminConfigServlet.class);

    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;

    @Inject
    public AdminConfigServlet(UserManager userManager,
                              LoginUriProvider loginUriProvider) {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Check authentication
        UserProfile user = userManager.getRemoteUser(req);
        if (user == null) {
            redirectToLogin(req, resp);
            return;
        }

        // Check admin permission
        if (!userManager.isSystemAdmin(user.getUserKey())) {
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

    private void redirectToLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        URI currentUri = URI.create(req.getRequestURL().toString());
        resp.sendRedirect(loginUriProvider.getLoginUri(currentUri).toASCIIString());
    }
}
