package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAuthMeDto;
import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import com.mywebsite.admin.service.AdminMenuService;
import com.mywebsite.admin.service.AdminPermissionQueryService;
import com.mywebsite.admin.testsupport.AdminRbacTestSupport;
import com.mywebsite.common.security.SubjectCodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A-3：{@code /api/admin/auth/me} 只加载一次 RBAC 快照，并显式传入菜单构建，
 * 不再让菜单服务二次加载快照；返回的 permissions 保持快照原样（不因菜单祖先补齐而扩张）。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthMeServiceImplTest {

    private static final long USER_ID = 100L;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private AdminPermissionQueryService permissionQueryService;

    @Mock
    private AdminMenuService adminMenuService;

    @AfterEach
    void tearDown() {
        AdminRbacTestSupport.clearRequestContext();
    }

    @Test
    void loadsSnapshotOnceAndPassesItToMenuBuild() {
        AdminRbacTestSupport.bindAdminRequestContext(USER_ID);
        String userCode = SubjectCodes.formatAdminSubject(USER_ID);

        AdminUserEntity entity = new AdminUserEntity();
        entity.setId(USER_ID);
        entity.setUsername("editor");
        entity.setDisplayName("Editor");
        entity.setStatus("ACTIVE");
        when(adminUserMapper.selectById(USER_ID)).thenReturn(entity);

        AdminCurrentAuthorizationSnapshot snapshot = new AdminCurrentAuthorizationSnapshot(
                USER_ID, List.of("editor"), List.of("admin:user:list", "admin:role:list"), false);
        when(permissionQueryService.loadForUserCode(userCode)).thenReturn(Optional.of(snapshot));

        List<AdminMenuNodeDto> menus =
                List.of(new AdminMenuNodeDto("admin:menu:system", "系统", null, null, 10, List.of(), 1L, "ACTIVE"));
        when(adminMenuService.buildMenuTree(snapshot)).thenReturn(menus);

        AdminAuthMeServiceImpl service =
                new AdminAuthMeServiceImpl(adminUserMapper, permissionQueryService, adminMenuService);
        AdminAuthMeDto result = service.loadCurrentAdminContext();

        // 快照只加载一次，且未走 loadForCurrentUser（菜单服务不再自行加载快照）。
        verify(permissionQueryService, times(1)).loadForUserCode(userCode);
        verify(permissionQueryService, never()).loadForCurrentUser();
        // 菜单构建收到的正是同一份快照。
        verify(adminMenuService, times(1)).buildMenuTree(eq(snapshot));

        // permissions 原样返回，未因菜单祖先补齐而混入容器 code。
        assertThat(result.permissions()).containsExactly("admin:user:list", "admin:role:list");
        assertThat(result.roles()).containsExactly("editor");
        assertThat(result.menus()).isSameAs(menus);
    }
}
