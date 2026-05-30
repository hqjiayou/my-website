package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleDetailDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleListItemDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleListQuery;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleUpdateRequest;
import java.util.List;

public interface AdminRoleManagementService {

    List<AdminRoleListItemDto> listRoles(AdminRoleListQuery query);

    AdminRoleDetailDto getRole(long id);

    AdminRoleDetailDto createRole(AdminRoleCreateRequest request);

    AdminRoleDetailDto updateRole(long id, AdminRoleUpdateRequest request);

    void deleteRole(long id);
}
