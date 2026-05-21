package com.mywebsite.admin.rbac.service;

import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleCreateRequest;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleDetailDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleListItemDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleListQuery;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleUpdateRequest;
import java.util.List;

public interface AdminRoleManagementService {

    List<AdminRoleListItemDto> listRoles(AdminRoleListQuery query);

    AdminRoleDetailDto getRole(long id);

    AdminRoleDetailDto createRole(AdminRoleCreateRequest request);

    AdminRoleDetailDto updateRole(long id, AdminRoleUpdateRequest request);

    void deleteRole(long id);
}
