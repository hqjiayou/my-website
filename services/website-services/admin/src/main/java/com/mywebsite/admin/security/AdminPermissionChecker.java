package com.mywebsite.admin.security;

import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Method Security SpEL 目标 Bean：{@code @PreAuthorize("@adminPermissionChecker.hasPermission('...')")}。
 */
@Component("adminPermissionChecker")
public class AdminPermissionChecker {

    private final CurrentAdminAuthorizationHolder authorizationHolder;

    public AdminPermissionChecker(CurrentAdminAuthorizationHolder authorizationHolder) {
        this.authorizationHolder = authorizationHolder;
    }

    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        var auth = authorizationHolder.get();
        if (!auth.authenticated()) {
            return false;
        }
        if (auth.superAdmin()) {
            return true;
        }
        return auth.permissions().contains(permissionCode);
    }

    public boolean hasAnyPermission(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return false;
        }
        return permissionCodes.stream().anyMatch(this::hasPermission);
    }

    public boolean hasRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        var auth = authorizationHolder.get();
        if (!auth.authenticated()) {
            return false;
        }
        return auth.roles().contains(roleCode);
    }
}
