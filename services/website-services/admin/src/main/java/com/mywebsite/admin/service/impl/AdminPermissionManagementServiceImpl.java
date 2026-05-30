package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminPermissionItemDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminPermissionListQuery;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.service.AdminPermissionManagementService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminPermissionManagementServiceImpl implements AdminPermissionManagementService {

    private final AdminPermissionMapper adminPermissionMapper;

    public AdminPermissionManagementServiceImpl(AdminPermissionMapper adminPermissionMapper) {
        this.adminPermissionMapper = adminPermissionMapper;
    }

    @Override
    public List<AdminPermissionItemDto> listPermissions(AdminPermissionListQuery query) {
        LambdaQueryWrapper<AdminPermissionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminPermissionEntity::getStatus, AdminRbacConstants.STATUS_ACTIVE);
        if (query != null) {
            if (StringUtils.hasText(query.permissionCode())) {
                wrapper.like(AdminPermissionEntity::getPermissionCode, query.permissionCode());
            }
            if (StringUtils.hasText(query.resourceType())) {
                wrapper.eq(AdminPermissionEntity::getResourceType, query.resourceType());
            }
            if (StringUtils.hasText(query.resourceKey())) {
                wrapper.like(AdminPermissionEntity::getResourceKey, query.resourceKey());
            }
        }
        wrapper.orderByAsc(AdminPermissionEntity::getPermissionCode);
        return adminPermissionMapper.selectList(wrapper).stream().map(this::toItem).toList();
    }

    private AdminPermissionItemDto toItem(AdminPermissionEntity entity) {
        return new AdminPermissionItemDto(
                entity.getId(),
                entity.getPermissionCode(),
                entity.getPermissionName(),
                entity.getResourceType(),
                entity.getResourceKey(),
                entity.getStatus());
    }
}
