package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuUpdateRequest;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import java.util.List;

public interface AdminMenuService {

    /** 依据给定 RBAC 快照构建可见菜单树（用于 {@code GET /api/admin/auth/me}，仅 ACTIVE）。 */
    List<AdminMenuNodeDto> buildMenuTree(AdminCurrentAuthorizationSnapshot authorization);

    /** 管理端菜单树：含停用，不含已软删。 */
    List<AdminMenuNodeDto> listManageTree();

    AdminMenuNodeDto createMenu(AdminMenuCreateRequest request);

    AdminMenuNodeDto updateMenu(long id, AdminMenuUpdateRequest request);

    void deleteMenu(long id);
}
