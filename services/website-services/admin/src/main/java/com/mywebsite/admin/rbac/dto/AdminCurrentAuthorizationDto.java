package com.mywebsite.admin.rbac.dto;

import java.util.List;

/** 当前管理员在 Admin 内的角色与权限快照（来自 DB，非 JWT）。 */
public record AdminCurrentAuthorizationDto(
        long userId, List<String> roles, List<String> permissions, boolean superAdmin) {

    public static AdminCurrentAuthorizationDto empty() {
        return new AdminCurrentAuthorizationDto(0L, List.of(), List.of(), false);
    }

    public boolean authenticated() {
        return userId > 0L;
    }
}
