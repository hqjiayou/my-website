package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsResponse;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import java.util.List;

public interface AdminRolePermissionManagementService {

    AdminRolePermissionsResponse getRolePermissions(long roleId);

    AdminRolePermissionsResponse replaceRolePermissions(long roleId, AdminRolePermissionsUpdateRequest request);
}
