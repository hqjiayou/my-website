package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminUserDtos.AdminUserCreateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserDetailDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListItemDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListQuery;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserUpdateRequest;
import java.util.List;

public interface AdminUserManagementService {

    List<AdminUserListItemDto> listUsers(AdminUserListQuery query);

    AdminUserDetailDto getUser(long id);

    AdminUserDetailDto createUser(AdminUserCreateRequest request);

    AdminUserDetailDto updateUser(long id, AdminUserUpdateRequest request);

    AdminUserDetailDto updateStatus(long id, AdminUserStatusUpdateRequest request);

    void deleteUser(long id);
}
