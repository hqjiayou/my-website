package com.mywebsite.admin.rbac.security;

import com.mywebsite.admin.rbac.dto.AdminCurrentAuthorizationDto;
import com.mywebsite.admin.rbac.service.AdminPermissionQueryService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** 请求级缓存当前用户 RBAC 快照，避免同一请求内重复查库。 */
@RequestScope
@Component
public class CurrentAdminAuthorizationHolder {

    private final AdminPermissionQueryService permissionQueryService;

    private boolean loaded;

    private AdminCurrentAuthorizationDto snapshot = AdminCurrentAuthorizationDto.empty();

    public CurrentAdminAuthorizationHolder(AdminPermissionQueryService permissionQueryService) {
        this.permissionQueryService = permissionQueryService;
    }

    public AdminCurrentAuthorizationDto get() {
        if (!loaded) {
            snapshot = permissionQueryService
                    .loadForCurrentUser()
                    .orElse(AdminCurrentAuthorizationDto.empty());
            loaded = true;
        }
        return snapshot;
    }
}
