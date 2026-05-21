package com.mywebsite.admin.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.entity.AdminUserRoleEntity;
import com.mywebsite.admin.mapper.AdminRoleMapper;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.mapper.AdminUserRoleMapper;
import com.mywebsite.admin.rbac.AdminRbacConstants;
import com.mywebsite.admin.rbac.AdminRbacMessageKeys;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleBindingDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminUserRoleResponse;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminUserRoleUpdateRequest;
import com.mywebsite.admin.rbac.mapper.AdminUserRoleManagementMapper;
import com.mywebsite.admin.rbac.mapper.AdminUserRoleManagementMapper.AdminUserRoleBindingRow;
import com.mywebsite.admin.rbac.service.AdminPermissionQueryService;
import com.mywebsite.admin.rbac.service.AdminUserRoleManagementService;
import com.mywebsite.admin.rbac.support.AdminSuperAdminRoleSupport;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserRoleManagementServiceImpl implements AdminUserRoleManagementService {

    private final AdminUserMapper adminUserMapper;

    private final AdminRoleMapper adminRoleMapper;

    private final AdminUserRoleMapper adminUserRoleMapper;

    private final AdminUserRoleManagementMapper adminUserRoleManagementMapper;

    private final AdminPermissionQueryService adminPermissionQueryService;

    public AdminUserRoleManagementServiceImpl(
            AdminUserMapper adminUserMapper,
            AdminRoleMapper adminRoleMapper,
            AdminUserRoleMapper adminUserRoleMapper,
            AdminUserRoleManagementMapper adminUserRoleManagementMapper,
            AdminPermissionQueryService adminPermissionQueryService) {
        this.adminUserMapper = adminUserMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.adminUserRoleMapper = adminUserRoleMapper;
        this.adminUserRoleManagementMapper = adminUserRoleManagementMapper;
        this.adminPermissionQueryService = adminPermissionQueryService;
    }

    @Override
    public AdminUserRoleResponse getUserRoles(long userId) {
        AdminUserEntity user = requireUser(userId);
        return toResponse(user, adminUserRoleManagementMapper.selectRoleBindingsByUserId(userId));
    }

    @Override
    @Transactional
    public AdminUserRoleResponse replaceUserRoles(long userId, AdminUserRoleUpdateRequest request) {
        AdminUserEntity user = requireUser(userId);
        long currentUserId = requireCurrentUserId();

        if (userId == currentUserId) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.USER_ROLE_SELF_UPDATE_DENIED);
        }
        if (adminPermissionQueryService.isSuperAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.SUPER_ADMIN_PROTECTED);
        }

        List<String> requested = request.roleCodes() == null ? List.of() : request.roleCodes();
        if (requested.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.USER_ROLE_EMPTY);
        }

        Set<String> uniqueCodes = new HashSet<>();
        List<AdminRoleEntity> resolvedRoles = new ArrayList<>();
        for (String roleCode : requested) {
            if (!StringUtils.hasText(roleCode)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.USER_ROLE_EMPTY);
            }
            String code = roleCode.trim();
            if (!uniqueCodes.add(code)) {
                continue;
            }
            AdminSuperAdminRoleSupport.rejectSuperAdminRoleCode(code);
            AdminRoleEntity role = adminRoleMapper.selectOne(
                    new LambdaQueryWrapper<AdminRoleEntity>()
                            .eq(AdminRoleEntity::getRoleCode, code)
                            .eq(AdminRoleEntity::getStatus, AdminRbacConstants.STATUS_ACTIVE));
            if (role == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.ROLE_NOT_FOUND);
            }
            resolvedRoles.add(role);
        }

        if (resolvedRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.USER_ROLE_EMPTY);
        }

        List<AdminUserRoleEntity> existing = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRoleEntity>().eq(AdminUserRoleEntity::getAdminUserId, userId));
        for (AdminUserRoleEntity link : existing) {
            adminUserRoleMapper.deleteById(link.getId());
        }

        for (AdminRoleEntity role : resolvedRoles) {
            AdminUserRoleEntity link = new AdminUserRoleEntity();
            link.setAdminUserId(userId);
            link.setAdminRoleId(role.getId());
            adminUserRoleMapper.insert(link);
        }

        return toResponse(user, adminUserRoleManagementMapper.selectRoleBindingsByUserId(userId));
    }

    private AdminUserEntity requireUser(long userId) {
        AdminUserEntity user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminRbacMessageKeys.USER_NOT_FOUND);
        }
        return user;
    }

    private long requireCurrentUserId() {
        return AuthContext.currentUser()
                .filter(u -> u.audience() == TokenAudience.ADMIN)
                .flatMap(u -> SubjectCodes.parseAdminUserId(u.subject()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "auth.unauthorized"));
    }

    private AdminUserRoleResponse toResponse(AdminUserEntity user, List<AdminUserRoleBindingRow> bindings) {
        List<AdminRoleBindingDto> roles = bindings.stream().map(this::toBindingDto).toList();
        return new AdminUserRoleResponse(
                user.getId(),
                SubjectCodes.formatAdminSubject(user.getId()),
                user.getUsername(),
                roles);
    }

    private AdminRoleBindingDto toBindingDto(AdminUserRoleBindingRow row) {
        boolean protectedRole = AdminSuperAdminRoleSupport.isSuperAdminRoleCode(row.roleCode());
        return new AdminRoleBindingDto(
                row.roleId(), row.roleCode(), row.roleName(), row.status(), protectedRole);
    }
}
