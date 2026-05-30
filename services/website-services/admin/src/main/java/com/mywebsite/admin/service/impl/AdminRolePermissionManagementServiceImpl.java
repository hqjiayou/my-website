package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.admin.entity.AdminRolePermissionEntity;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.mapper.AdminRoleMapper;
import com.mywebsite.admin.mapper.AdminRolePermissionMapper;
import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.constant.AdminRbacMessageKeys;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsResponse;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import com.mywebsite.admin.mapper.AdminRbacQueryMapper;
import com.mywebsite.admin.service.AdminRolePermissionManagementService;
import com.mywebsite.admin.service.AdminRoleGuard;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminRolePermissionManagementServiceImpl implements AdminRolePermissionManagementService {

    private final AdminRoleMapper adminRoleMapper;

    private final AdminPermissionMapper adminPermissionMapper;

    private final AdminRolePermissionMapper adminRolePermissionMapper;

    private final AdminRbacQueryMapper adminRbacQueryMapper;

    public AdminRolePermissionManagementServiceImpl(
            AdminRoleMapper adminRoleMapper,
            AdminPermissionMapper adminPermissionMapper,
            AdminRolePermissionMapper adminRolePermissionMapper,
            AdminRbacQueryMapper adminRbacQueryMapper) {
        this.adminRoleMapper = adminRoleMapper;
        this.adminPermissionMapper = adminPermissionMapper;
        this.adminRolePermissionMapper = adminRolePermissionMapper;
        this.adminRbacQueryMapper = adminRbacQueryMapper;
    }

    @Override
    public AdminRolePermissionsResponse getRolePermissions(long roleId) {
        AdminRoleEntity role = requireRole(roleId);
        List<String> codes = adminRbacQueryMapper.selectPermissionCodesByRoleId(roleId);
        return new AdminRolePermissionsResponse(role.getId(), role.getRoleCode(), List.copyOf(codes));
    }

    @Override
    @Transactional
    public AdminRolePermissionsResponse replaceRolePermissions(
            long roleId, AdminRolePermissionsUpdateRequest request) {
        AdminRoleEntity role = requireRole(roleId);
        AdminRoleGuard.rejectIfSuperAdmin(role);

        List<String> requested = request.permissionCodes() == null ? List.of() : request.permissionCodes();
        Set<String> unique = new HashSet<>();
        for (String code : requested) {
            if (!StringUtils.hasText(code)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.PERMISSION_CODE_INVALID);
            }
            if (!unique.add(code.trim())) {
                continue;
            }
        }

        List<AdminPermissionEntity> resolved = new ArrayList<>();
        for (String code : unique) {
            AdminPermissionEntity permission = adminPermissionMapper.selectOne(
                    new LambdaQueryWrapper<AdminPermissionEntity>()
                            .eq(AdminPermissionEntity::getPermissionCode, code)
                            .eq(AdminPermissionEntity::getStatus, AdminRbacConstants.STATUS_ACTIVE));
            if (permission == null) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.PERMISSION_NOT_FOUND, code);
            }
            resolved.add(permission);
        }

        List<AdminRolePermissionEntity> existing = adminRolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermissionEntity>()
                        .eq(AdminRolePermissionEntity::getAdminRoleId, roleId));
        for (AdminRolePermissionEntity link : existing) {
            adminRolePermissionMapper.deleteById(link.getId());
        }

        for (AdminPermissionEntity permission : resolved) {
            AdminRolePermissionEntity link = new AdminRolePermissionEntity();
            link.setAdminRoleId(roleId);
            link.setAdminPermissionId(permission.getId());
            adminRolePermissionMapper.insert(link);
        }

        List<String> codes = resolved.stream().map(AdminPermissionEntity::getPermissionCode).sorted().toList();
        return new AdminRolePermissionsResponse(role.getId(), role.getRoleCode(), codes);
    }

    private AdminRoleEntity requireRole(long id) {
        AdminRoleEntity role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminRbacMessageKeys.ROLE_NOT_FOUND);
        }
        return role;
    }
}
