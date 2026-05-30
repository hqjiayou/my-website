package com.mywebsite.admin.security;

import com.mywebsite.admin.service.AdminPermissionQueryService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** 请求级缓存当前用户 RBAC 快照，避免同一请求内重复查库。 */
@RequestScope
@Component
public class CurrentAdminAuthorizationHolder {

    private final AdminPermissionQueryService permissionQueryService;

    private boolean loaded;

    private AdminCurrentAuthorizationSnapshot snapshot = AdminCurrentAuthorizationSnapshot.empty();

    public CurrentAdminAuthorizationHolder(AdminPermissionQueryService permissionQueryService) {
        this.permissionQueryService = permissionQueryService;
    }

    public AdminCurrentAuthorizationSnapshot get() {
        if (!loaded) {
            snapshot = permissionQueryService
                    .loadForCurrentUser()
                    .orElse(AdminCurrentAuthorizationSnapshot.empty());
            loaded = true;
        }
        return snapshot;
    }
}
