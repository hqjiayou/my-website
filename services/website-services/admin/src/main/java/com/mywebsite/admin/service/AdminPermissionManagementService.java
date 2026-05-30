package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminPermissionItemDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminPermissionListQuery;
import java.util.List;

public interface AdminPermissionManagementService {

    List<AdminPermissionItemDto> listPermissions(AdminPermissionListQuery query);
}
