package com.mywebsite.admin.rbac.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.admin.mapper.AdminRoleMapper;
import com.mywebsite.admin.rbac.AdminRbacConstants;
import com.mywebsite.admin.rbac.AdminRbacMessageKeys;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleCreateRequest;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleDetailDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleListItemDto;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleListQuery;
import com.mywebsite.admin.rbac.dto.AdminRbacDtos.AdminRoleUpdateRequest;
import com.mywebsite.admin.rbac.mapper.AdminRoleManagementMapper;
import com.mywebsite.admin.rbac.service.AdminRoleManagementService;
import com.mywebsite.admin.rbac.support.AdminSuperAdminRoleSupport;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminRoleManagementServiceImpl implements AdminRoleManagementService {

    private final AdminRoleMapper adminRoleMapper;

    private final AdminRoleManagementMapper adminRoleManagementMapper;

    public AdminRoleManagementServiceImpl(
            AdminRoleMapper adminRoleMapper, AdminRoleManagementMapper adminRoleManagementMapper) {
        this.adminRoleMapper = adminRoleMapper;
        this.adminRoleManagementMapper = adminRoleManagementMapper;
    }

    @Override
    public List<AdminRoleListItemDto> listRoles(AdminRoleListQuery query) {
        LambdaQueryWrapper<AdminRoleEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.roleCode())) {
                wrapper.eq(AdminRoleEntity::getRoleCode, query.roleCode());
            }
            if (StringUtils.hasText(query.roleName())) {
                wrapper.like(AdminRoleEntity::getRoleName, query.roleName());
            }
            if (StringUtils.hasText(query.status())) {
                wrapper.eq(AdminRoleEntity::getStatus, query.status());
            }
        }
        wrapper.orderByAsc(AdminRoleEntity::getRoleCode);
        return adminRoleMapper.selectList(wrapper).stream().map(this::toListItem).toList();
    }

    @Override
    public AdminRoleDetailDto getRole(long id) {
        return toDetail(requireRole(id));
    }

    @Override
    public AdminRoleDetailDto createRole(AdminRoleCreateRequest request) {
        AdminSuperAdminRoleSupport.rejectSuperAdminRoleCode(request.roleCode());
        if (!request.roleCode().matches("^[a-z][a-z0-9_]*$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.ROLE_CODE_INVALID);
        }
        if (findActiveByRoleCode(request.roleCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminRbacMessageKeys.ROLE_CODE_DUPLICATE);
        }
        AdminRoleEntity entity = new AdminRoleEntity();
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setDescription(request.description());
        entity.setStatus(request.status());
        try {
            adminRoleMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminRbacMessageKeys.ROLE_CODE_DUPLICATE);
        }
        return toDetail(entity);
    }

    @Override
    public AdminRoleDetailDto updateRole(long id, AdminRoleUpdateRequest request) {
        AdminRoleEntity role = requireRole(id);
        AdminSuperAdminRoleSupport.rejectIfSuperAdmin(role);
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        role.setStatus(request.status());
        adminRoleMapper.updateById(role);
        return toDetail(role);
    }

    @Override
    public void deleteRole(long id) {
        AdminRoleEntity role = requireRole(id);
        AdminSuperAdminRoleSupport.rejectIfSuperAdmin(role);
        if (adminRoleManagementMapper.countActiveUsersByRoleId(id) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.ROLE_IN_USE);
        }
        adminRoleMapper.deleteById(id);
    }

    private AdminRoleEntity requireRole(long id) {
        AdminRoleEntity role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminRbacMessageKeys.ROLE_NOT_FOUND);
        }
        return role;
    }

    private AdminRoleEntity findActiveByRoleCode(String roleCode) {
        return adminRoleMapper.selectOne(
                new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getRoleCode, roleCode));
    }

    private AdminRoleListItemDto toListItem(AdminRoleEntity entity) {
        return new AdminRoleListItemDto(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getStatus(),
                entity.getDescription());
    }

    private AdminRoleDetailDto toDetail(AdminRoleEntity entity) {
        return new AdminRoleDetailDto(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getStatus(),
                entity.getDescription());
    }
}
