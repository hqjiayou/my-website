package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.AdminAuthMeDto;
import com.mywebsite.admin.dto.AdminAuthMeUserDto;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.service.AdminAuthMeService;
import com.mywebsite.admin.service.AdminPermissionQueryService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthMeServiceImpl implements AdminAuthMeService {

    private final AdminUserMapper adminUserMapper;

    private final AdminPermissionQueryService permissionQueryService;

    public AdminAuthMeServiceImpl(AdminUserMapper adminUserMapper, AdminPermissionQueryService permissionQueryService) {
        this.adminUserMapper = adminUserMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @Override
    public AdminAuthMeDto loadCurrentAdminContext() {
        String subject =
                AuthContext.currentUser().map(u -> u.subject()).orElseThrow(IllegalStateException::new);
        long userId = SubjectCodes.parseAdminUserId(subject)
                .orElseThrow(() -> new IllegalStateException("invalid admin subject in security context"));

        AdminUserEntity entity = adminUserMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessageKey());
        }

        String userCode = SubjectCodes.formatAdminSubject(userId);
        AdminAuthMeUserDto user = new AdminAuthMeUserDto(
                userCode,
                entity.getUsername(),
                entity.getDisplayName(),
                null,
                entity.getStatus());

        var authorization = permissionQueryService
                .loadForUserCode(userCode)
                .orElseThrow(() -> new IllegalStateException("authenticated admin user missing RBAC snapshot"));

        return new AdminAuthMeDto(user, authorization.roles(), authorization.permissions(), List.of());
    }
}
