package com.mywebsite.admin.dto.authme;

import java.util.List;

/** GET /api/admin/auth/me 载荷（RBAC 深化前：角色/权限/菜单为占位示例）。 */
public record AdminAuthMeResponse(
        AdminAuthMeUser user, List<String> roles, List<String> permissions, List<AdminMenuNode> menus) {}
