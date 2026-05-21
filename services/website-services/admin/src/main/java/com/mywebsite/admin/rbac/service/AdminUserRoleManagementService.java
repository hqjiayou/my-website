package com.mywebsite.admin.rbac.service;

import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminUserRoleResponse;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminUserRoleUpdateRequest;

public interface AdminUserRoleManagementService {

    AdminUserRoleResponse getUserRoles(long userId);

    AdminUserRoleResponse replaceUserRoles(long userId, AdminUserRoleUpdateRequest request);
}
