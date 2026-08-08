package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import java.util.List;

public interface AdminMenuService {

    /** 依据给定 RBAC 快照构建可见菜单树（用于 {@code GET /api/admin/auth/me}）。 */
    List<AdminMenuNodeDto> buildMenuTree(AdminCurrentAuthorizationSnapshot authorization);

    /** 全部 ACTIVE 菜单树（管理端只读，需 {@code admin:menu:list}）。 */
    List<AdminMenuNodeDto> listActiveMenuTree();
}
