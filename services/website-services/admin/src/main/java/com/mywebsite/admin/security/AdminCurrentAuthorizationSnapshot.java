package com.mywebsite.admin.security;

import java.util.List;

/** 当前管理员在 Admin 内的角色与权限快照（来自 DB，非 JWT / 非 HTTP 契约）。 */
public record AdminCurrentAuthorizationSnapshot(
        long userId, List<String> roles, List<String> permissions, boolean superAdmin) {

    public static AdminCurrentAuthorizationSnapshot empty() {
        return new AdminCurrentAuthorizationSnapshot(0L, List.of(), List.of(), false);
    }

    public boolean authenticated() {
        return userId > 0L;
    }
}
