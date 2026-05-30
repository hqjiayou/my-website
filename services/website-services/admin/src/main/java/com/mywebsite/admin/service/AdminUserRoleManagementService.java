package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminUserRoleResponse;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminUserRoleUpdateRequest;

public interface AdminUserRoleManagementService {

    AdminUserRoleResponse getUserRoles(long userId);

    AdminUserRoleResponse replaceUserRoles(long userId, AdminUserRoleUpdateRequest request);
}
