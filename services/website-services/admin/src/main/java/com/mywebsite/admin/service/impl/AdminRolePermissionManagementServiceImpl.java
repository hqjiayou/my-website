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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminRolePermissionManagementServiceImpl implements AdminRolePermissionManagementService {

    private static final String RESOURCE_TYPE_ACTION = "ACTION";

    /**
     * 页面菜单对应的按钮/接口权限前缀。勾选菜单时一并写入，避免再逐个勾选按钮权限。
     * {@code admin:menu:} 只匹配 ACTION（如 {@code admin:menu:list}），不匹配 {@code admin:menu:system.*} 这类菜单码。
     */
    private static final Map<String, String> MENU_ACTION_PREFIX = Map.of(
            "admin:menu:system.user", "admin:user:",
            "admin:menu:system.role", "admin:role:",
            "admin:menu:system.menu", "admin:menu:",
            "admin:menu:apps.sites", "admin:agent:");

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
            unique.add(code.trim());
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
        resolved = expandMenus(roleId, resolved);

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

    private List<AdminPermissionEntity> expandMenus(long roleId, List<AdminPermissionEntity> requested) {
        boolean hasMenu = requested.stream().anyMatch(this::isMenu);
        if (!hasMenu) {
            return requested;
        }
        List<AdminPermissionEntity> active = adminPermissionMapper.selectList(
                new LambdaQueryWrapper<AdminPermissionEntity>()
                        .eq(AdminPermissionEntity::getStatus, AdminRbacConstants.STATUS_ACTIVE));
        Map<Long, AdminPermissionEntity> included = new LinkedHashMap<>();
        for (AdminPermissionEntity item : requested) {
            included.put(item.getId(), item);
            if (isMenu(item)) {
                addAncestors(included, item);
                addMenuActions(included, active, item.getPermissionCode());
            }
        }
        keepUnmappedActions(included, roleId);
        return new ArrayList<>(included.values());
    }

    private void addAncestors(Map<Long, AdminPermissionEntity> included, AdminPermissionEntity menu) {
        Long parentId = menu.getParentId();
        while (parentId != null && !included.containsKey(parentId)) {
            AdminPermissionEntity parent = adminPermissionMapper.selectById(parentId);
            if (parent == null || !isMenu(parent) || !AdminRbacConstants.STATUS_ACTIVE.equals(parent.getStatus())) {
                break;
            }
            included.put(parent.getId(), parent);
            parentId = parent.getParentId();
        }
    }

    private static void addMenuActions(
            Map<Long, AdminPermissionEntity> included, List<AdminPermissionEntity> active, String menuCode) {
        String prefix = MENU_ACTION_PREFIX.get(menuCode);
        if (prefix == null) {
            return;
        }
        for (AdminPermissionEntity permission : active) {
            if (isDerivedAction(permission, prefix)) {
                included.put(permission.getId(), permission);
            }
        }
    }

    /** 没有菜单对应关系的接口权限（如 AI、内容）保留原绑定，避免只改菜单时被清掉。 */
    private void keepUnmappedActions(Map<Long, AdminPermissionEntity> included, long roleId) {
        List<AdminRolePermissionEntity> links = adminRolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermissionEntity>()
                        .eq(AdminRolePermissionEntity::getAdminRoleId, roleId));
        for (AdminRolePermissionEntity link : links) {
            if (included.containsKey(link.getAdminPermissionId())) {
                continue;
            }
            AdminPermissionEntity current = adminPermissionMapper.selectById(link.getAdminPermissionId());
            if (current == null || !AdminRbacConstants.STATUS_ACTIVE.equals(current.getStatus())) {
                continue;
            }
            if (RESOURCE_TYPE_ACTION.equals(current.getResourceType()) && !isMappedAction(current.getPermissionCode())) {
                included.put(current.getId(), current);
            }
        }
    }

    private boolean isMenu(AdminPermissionEntity permission) {
        return AdminRbacConstants.RESOURCE_TYPE_MENU.equals(permission.getResourceType());
    }

    private static boolean isDerivedAction(AdminPermissionEntity permission, String prefix) {
        if (!RESOURCE_TYPE_ACTION.equals(permission.getResourceType())) {
            return false;
        }
        String code = permission.getPermissionCode();
        if (!code.startsWith(prefix)) {
            return false;
        }
        if ("admin:menu:".equals(prefix)) {
            return !code.substring(prefix.length()).contains(".");
        }
        return true;
    }

    private static boolean isMappedAction(String code) {
        for (String prefix : MENU_ACTION_PREFIX.values()) {
            if (!code.startsWith(prefix)) {
                continue;
            }
            if ("admin:menu:".equals(prefix)) {
                return !code.substring(prefix.length()).contains(".");
            }
            return true;
        }
        return false;
    }
}
