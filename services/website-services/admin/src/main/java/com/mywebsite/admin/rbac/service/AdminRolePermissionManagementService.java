package com.mywebsite.admin.rbac.service;

import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRolePermissionsResponse;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import java.util.List;

public interface AdminRolePermissionManagementService {

    AdminRolePermissionsResponse getRolePermissions(long roleId);

    AdminRolePermissionsResponse replaceRolePermissions(long roleId, AdminRolePermissionsUpdateRequest request);
}
