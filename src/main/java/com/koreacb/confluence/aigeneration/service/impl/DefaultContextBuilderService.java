package com.koreacb.confluence.aigeneration.service.impl;

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
import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.labels.LabelManager;
import com.koreacb.confluence.aigeneration.model.GenerationRequest;
import com.koreacb.confluence.aigeneration.model.PromptContext;
import com.koreacb.confluence.aigeneration.service.ContextBuilderService;
import com.koreacb.confluence.aigeneration.util.ContentTruncator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

@Named("contextBuilderService")
public class DefaultContextBuilderService implements ContextBuilderService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultContextBuilderService.class);
    private static final int MAX_PAGE_LEN = 10000;

    private final PageManager pageManager;
    private final AttachmentManager attachmentManager;
    private final SpaceManager spaceManager;
    private final LabelManager labelManager;
    private final PermissionManager permissionManager;
    private final UserAccessor userAccessor;
    private final ContentTruncator contentTruncator;

    @Inject
    public DefaultContextBuilderService(PageManager pageManager, AttachmentManager attachmentManager,
                                        SpaceManager spaceManager, LabelManager labelManager,
                                        PermissionManager permissionManager, UserAccessor userAccessor,
                                        ContentTruncator contentTruncator) {
        this.pageManager = pageManager;
        this.attachmentManager = attachmentManager;
        this.spaceManager = spaceManager;
        this.labelManager = labelManager;
        this.permissionManager = permissionManager;
        this.userAccessor = userAccessor;
        this.contentTruncator = contentTruncator;
    }

    @Override
    public PromptContext buildPromptContext(GenerationRequest req, String userKey) {
        PromptContext ctx = new PromptContext();
        if (req.getContextPageIds() != null && !req.getContextPageIds().isEmpty())
            ctx.setPageContents(extractPageContent(req.getContextPageIds(), userKey));
        if (req.getAttachmentIds() != null && !req.getAttachmentIds().isEmpty())
            ctx.setAttachmentTexts(extractAttachmentText(req.getAttachmentIds(), userKey));
        ctx.setLabels(req.getLabels() != null ? req.getLabels() : extractLabels(req.getSpaceKey()));
        if (req.getSpaceKey() != null) {
            Space space = spaceManager.getSpace(req.getSpaceKey());
            if (space != null && space.getDescription() != null)
                ctx.setSpaceDescription(space.getDescription().getBodyAsString());
        }
        ctx.setAdditionalContext(req.getAdditionalContext());
        return ctx;
    }

    @Override
    public Map<Long, String> extractPageContent(List<Long> pageIds, String userKey) {
        Map<Long, String> r = new LinkedHashMap<>();
        ConfluenceUser user = resolveUser(userKey);
        for (Long pid : pageIds) {
            try {
                Page page = pageManager.getPage(pid);
                if (page == null || !permissionManager.hasPermission(user, Permission.VIEW, page)) continue;
                String plain = stripXhtml(page.getBodyAsString());
                r.put(pid, "Title: " + page.getTitle() + "\n" + contentTruncator.truncate(plain, MAX_PAGE_LEN));
            } catch (Exception e) { LOG.error("Error extracting page {}", pid, e); }
        }
        return r;
    }

    @Override
    public Map<Long, String> extractAttachmentText(List<Long> attachmentIds, String userKey) {
        Map<Long, String> r = new LinkedHashMap<>();
        ConfluenceUser user = resolveUser(userKey);
        for (Long aid : attachmentIds) {
            try {
                Attachment att = attachmentManager.getAttachment(aid);
                if (att == null) continue;
                Page parent = (Page) att.getContainer();
                if (!permissionManager.hasPermission(user, Permission.VIEW, parent)) continue;
                r.put(aid, "Attachment: " + att.getFileName() + " (" + att.getMediaType() + ")");
            } catch (Exception e) { LOG.error("Error extracting attachment {}", aid, e); }
        }
        return r;
    }

    @Override
    public List<String> extractLabels(String spaceKey) {
        List<String> names = new ArrayList<>();
        try {
            Space space = spaceManager.getSpace(spaceKey);
            if (space != null) {
                // Get labels from pages in the space
                List<Page> pages = pageManager.getPages(space, true);
                Set<String> seen = new HashSet<>();
                for (Page page : pages) {
                    List<Label> labels = page.getLabels();
                    for (Label l : labels) {
                        if (seen.add(l.getName())) {
                            names.add(l.getName());
                        }
                    }
                    if (names.size() >= 50) break; // Limit label collection
                }
            }
        } catch (Exception e) { LOG.warn("Error extracting labels for {}", spaceKey); }
        return names;
    }

    private ConfluenceUser resolveUser(String userKey) {
        if (userKey == null) return null;
        // Try by SAL user key first
        ConfluenceUser user = userAccessor.getExistingUserByKey(
                new com.atlassian.sal.api.user.UserKey(userKey));
        if (user == null) {
            // Fallback: try as username
            user = userAccessor.getUserByName(userKey);
        }
        return user;
    }

    private String stripXhtml(String x) {
        return x != null ? x.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim() : "";
    }
}
