package com.mywebsite.site.dto.authme;

import java.util.List;

/** GET /api/site/auth/me 载荷（占位：权限 / 菜单后续接入 RBAC）。 */
public record SiteAuthMeResponse(
        SiteAuthMeUser user, List<String> roles, List<String> permissions, List<SiteMenuNode> menus) {}
