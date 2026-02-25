package com.koreacb.confluence.aigeneration.security;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;

@Named("permissionChecker")
public class PermissionChecker {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionChecker.class);

    private final PermissionManager permissionManager;
    private final PageManager pageManager;
    private final SpaceManager spaceManager;
    private final UserAccessor userAccessor;

    @Inject
    public PermissionChecker(@ComponentImport PermissionManager permissionManager,
                             @ComponentImport PageManager pageManager,
                             @ComponentImport SpaceManager spaceManager,
                             @ComponentImport UserAccessor userAccessor) {
        this.permissionManager = permissionManager;
        this.pageManager = pageManager;
        this.spaceManager = spaceManager;
        this.userAccessor = userAccessor;
    }

    public boolean canUserGenerate(ConfluenceUser user, String spaceKey) {
        if (user == null) return false;
        Space space = spaceManager.getSpace(spaceKey);
        if (space == null) return false;
        return permissionManager.hasPermission(user, Permission.EDIT, space);
    }

    public boolean canUserViewPage(ConfluenceUser user, long pageId) {
        if (user == null) return false;
        Page page = pageManager.getPage(pageId);
        if (page == null) return false;
        return permissionManager.hasPermission(user, Permission.VIEW, page);
    }

    public boolean canUserEditPage(ConfluenceUser user, long pageId) {
        if (user == null) return false;
        Page page = pageManager.getPage(pageId);
        if (page == null) return false;
        return permissionManager.hasPermission(user, Permission.EDIT, page);
    }

    public boolean canUserCreatePage(ConfluenceUser user, String spaceKey) {
        if (user == null) return false;
        Space space = spaceManager.getSpace(spaceKey);
        if (space == null) return false;
        return permissionManager.hasCreatePermission(user, space, Page.class);
    }

    public boolean canUserAdmin(ConfluenceUser user) {
        if (user == null) return false;
        return permissionManager.isConfluenceAdministrator(user);
    }

    public ConfluenceUser getUser(String userKey) {
        if (userKey == null) return null;
        // Try by key first, then by name
        ConfluenceUser user = userAccessor.getExistingUserByKey(
                new com.atlassian.sal.api.user.UserKey(userKey));
        if (user == null) {
            // Fallback: try as username
            user = userAccessor.getUserByName(userKey);
        }
        return user;
    }

    public boolean isUserInGroup(String userKey, String groupName) {
        ConfluenceUser user = getUser(userKey);
        if (user == null) return false;
        try {
            return userAccessor.hasMembership(groupName, user.getName());
        } catch (Exception e) {
            LOG.warn("Error checking group membership for user {} in group {}", userKey, groupName);
            return false;
        }
    }
}
