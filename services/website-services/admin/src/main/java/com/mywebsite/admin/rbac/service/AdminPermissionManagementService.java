package com.mywebsite.admin.rbac.service;

import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminPermissionItemDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminPermissionListQuery;
import java.util.List;

public interface AdminPermissionManagementService {

    List<AdminPermissionItemDto> listPermissions(AdminPermissionListQuery query);
}
