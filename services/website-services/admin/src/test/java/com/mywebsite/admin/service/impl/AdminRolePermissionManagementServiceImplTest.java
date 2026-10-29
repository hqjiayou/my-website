package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.admin.entity.AdminRolePermissionEntity;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.mapper.AdminRbacQueryMapper;
import com.mywebsite.admin.mapper.AdminRoleMapper;
import com.mywebsite.admin.mapper.AdminRolePermissionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRolePermissionManagementServiceImplTest {

    @Mock
    private AdminRoleMapper adminRoleMapper;

    @Mock
    private AdminPermissionMapper adminPermissionMapper;

    @Mock
    private AdminRolePermissionMapper adminRolePermissionMapper;

    @Mock
    private AdminRbacQueryMapper adminRbacQueryMapper;

    private AdminRolePermissionManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminRolePermissionManagementServiceImpl(
                adminRoleMapper, adminPermissionMapper, adminRolePermissionMapper, adminRbacQueryMapper);
    }

    @Test
    void grantingUserMenuAlsoGrantsItsActionsAndKeepsUnmappedApiPermission() {
        when(adminRoleMapper.selectById(1L)).thenReturn(role(1L, "editor"));
        when(adminPermissionMapper.selectOne(any())).thenReturn(menu(14L, 11L, "admin:menu:system.user"));
        when(adminPermissionMapper.selectList(any()))
                .thenReturn(List.of(action(9L, "admin:user:list"), action(90L, "admin:ai-kb:list")));
        when(adminPermissionMapper.selectById(11L)).thenReturn(menu(11L, null, "admin:menu:system"));
        when(adminPermissionMapper.selectById(90L)).thenReturn(action(90L, "admin:ai-kb:list"));
        AdminRolePermissionEntity link = new AdminRolePermissionEntity();
        link.setAdminPermissionId(90L);
        when(adminRolePermissionMapper.selectList(any())).thenReturn(List.of(link));

        var response = service.replaceRolePermissions(1L, new AdminRolePermissionsUpdateRequest(List.of("admin:menu:system.user")));

        assertThat(response.permissions())
                .contains("admin:menu:system", "admin:menu:system.user", "admin:user:list", "admin:ai-kb:list");
    }

    @Test
    void actionOnlyRequestStaysAsWritten() {
        when(adminRoleMapper.selectById(1L)).thenReturn(role(1L, "editor"));
        when(adminPermissionMapper.selectOne(any())).thenReturn(action(9L, "admin:role:list"));
        when(adminRolePermissionMapper.selectList(any())).thenReturn(List.of());

        var response = service.replaceRolePermissions(1L, new AdminRolePermissionsUpdateRequest(List.of("admin:role:list")));

        assertThat(response.permissions()).containsExactly("admin:role:list");
        verify(adminPermissionMapper, never()).selectList(any());
    }

    private static AdminRoleEntity role(Long id, String code) {
        AdminRoleEntity role = new AdminRoleEntity();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }

    private static AdminPermissionEntity menu(Long id, Long parentId, String code) {
        AdminPermissionEntity entity = new AdminPermissionEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setPermissionCode(code);
        entity.setResourceType("MENU");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static AdminPermissionEntity action(Long id, String code) {
        AdminPermissionEntity entity = new AdminPermissionEntity();
        entity.setId(id);
        entity.setPermissionCode(code);
        entity.setResourceType("ACTION");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
