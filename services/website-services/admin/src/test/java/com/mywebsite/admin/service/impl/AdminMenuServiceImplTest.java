package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.constant.AdminRbacMessageKeys;
import com.mywebsite.admin.dto.AdminMenuNodeDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminMenuUpdateRequest;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import com.mywebsite.admin.mapper.AdminMenuMapper;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A-4 Menu Parent/Child 语义单元测试。
 *
 * <p>菜单固定层级（沿用 {@code Menu = admin_permission(resource_type=MENU)} 模型）：
 *
 * <pre>
 * [1] system    (parent=null, route=null)          -- 结构容器
 *   ├─ [2] user   (parent=1, route=/users)
 *   └─ [3] role   (parent=1, route=/roles)
 *        └─ [4] roleDetail (parent=3, route=/roles/:id)  -- 三级
 * [5] content   (parent=null, route=/content)
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class AdminMenuServiceImplTest {

    private static final String CODE_SYSTEM = "admin:menu:system";
    private static final String CODE_USER = "admin:user:list";
    private static final String CODE_ROLE = "admin:role:list";
    private static final String CODE_ROLE_DETAIL = "admin:role:detail";
    private static final String CODE_CONTENT = "admin:content:list";

    @Mock
    private AdminMenuMapper adminMenuMapper;

    @Mock
    private AdminPermissionMapper adminPermissionMapper;

    private AdminMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminMenuServiceImpl(adminMenuMapper, adminPermissionMapper);
        lenient().when(adminMenuMapper.selectAllActiveMenus()).thenReturn(sampleMenus());
    }

    @Test
    void superAdminSeesFullTreeRegardlessOfPermissions() {
        List<AdminMenuNodeDto> tree = service.buildMenuTree(snapshot(true /* superAdmin */));

        assertThat(codes(tree)).containsExactly(CODE_SYSTEM, CODE_CONTENT); // 按 sort_order：system(10) < content(20)
        AdminMenuNodeDto system = find(tree, CODE_SYSTEM);
        assertThat(codes(system.children())).containsExactly(CODE_USER, CODE_ROLE);
        assertThat(codes(find(system.children(), CODE_ROLE).children())).containsExactly(CODE_ROLE_DETAIL);
    }

    @Test
    void authorizedParentWithoutChildRendersAsLeafContainer() {
        // 仅授权父容器 system：子节点未授权，不应出现。
        List<AdminMenuNodeDto> tree = service.buildMenuTree(snapshot(false, CODE_SYSTEM));

        assertThat(codes(tree)).containsExactly(CODE_SYSTEM);
        assertThat(find(tree, CODE_SYSTEM).children()).isEmpty();
    }

    @Test
    void authorizedChildBackfillsAncestorAsContainerNotRoot() {
        // A-4 关键：仅授权子节点 user，父容器 system 未授权 —— system 应作为容器补入，
        // 且 user 挂在 system 之下，绝不被提升为根。
        List<AdminMenuNodeDto> tree = service.buildMenuTree(snapshot(false, CODE_USER));

        assertThat(codes(tree)).containsExactly(CODE_SYSTEM);
        AdminMenuNodeDto system = find(tree, CODE_SYSTEM);
        assertThat(system.path()).isNull(); // 结构容器：route_path 为 null
        assertThat(codes(system.children())).containsExactly(CODE_USER);
    }

    @Test
    void authorizedGrandchildBackfillsFullAncestorChain() {
        // 仅授权三级节点 roleDetail：应补齐 role + system 整条父链；同级 user、无关 content 不出现。
        List<AdminMenuNodeDto> tree = service.buildMenuTree(snapshot(false, CODE_ROLE_DETAIL));

        assertThat(codes(tree)).containsExactly(CODE_SYSTEM);
        AdminMenuNodeDto system = find(tree, CODE_SYSTEM);
        assertThat(codes(system.children())).containsExactly(CODE_ROLE);
        assertThat(codes(find(system.children(), CODE_ROLE).children())).containsExactly(CODE_ROLE_DETAIL);
    }

    @Test
    void unrelatedAuthorizationDoesNotLeakSiblings() {
        // 授权 content：只出现 content，system 子树完全不出现（无过度展开）。
        List<AdminMenuNodeDto> tree = service.buildMenuTree(snapshot(false, CODE_CONTENT));

        assertThat(codes(tree)).containsExactly(CODE_CONTENT);
    }

    @Test
    void emptyPermissionsYieldEmptyTreeForNonSuperAdmin() {
        assertThat(service.buildMenuTree(snapshot(false))).isEmpty();
    }

    @Test
    void disabledParentDoesNotPromoteChildToSidebarRoot() {
        when(adminMenuMapper.selectAllActiveMenus())
                .thenReturn(List.of(menu(2L, 1L, CODE_USER, "用户", "/users", 10)));

        assertThat(service.buildMenuTree(snapshot(true))).isEmpty();
    }

    @Test
    void createRejectsCodeOutsideMenuNamespace() {
        AdminMenuCreateRequest request =
                new AdminMenuCreateRequest("admin:user:list", "用户", null, "/system/users", "user", 10, "ACTIVE");

        BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.createMenu(request));
        assertThat(ex).isNotNull();
        assertThat(ex.getMessageKey()).isEqualTo(AdminRbacMessageKeys.MENU_CODE_INVALID);
    }

    @Test
    void createRejectsMissingParent() {
        when(adminPermissionMapper.selectById(99L)).thenReturn(null);
        AdminMenuCreateRequest request =
                new AdminMenuCreateRequest("admin:menu:extra", "额外", 99L, "/extra", "menu", 10, "ACTIVE");

        BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.createMenu(request));
        assertThat(ex).isNotNull();
        assertThat(ex.getMessageKey()).isEqualTo(AdminRbacMessageKeys.MENU_PARENT_INVALID);
    }

    @Test
    void updateRejectsMovingUnderOwnDescendant() {
        when(adminPermissionMapper.selectById(1L)).thenReturn(menu(1L, null, CODE_SYSTEM, "系统", null, 10));
        when(adminPermissionMapper.selectById(2L)).thenReturn(menu(2L, 1L, CODE_USER, "用户", "/users", 10));
        when(adminMenuMapper.selectAllMenus()).thenReturn(sampleMenus());
        AdminMenuUpdateRequest request = new AdminMenuUpdateRequest("系统", 2L, null, "setting", 10, "ACTIVE");

        BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.updateMenu(1L, request));
        assertThat(ex).isNotNull();
        assertThat(ex.getMessageKey()).isEqualTo(AdminRbacMessageKeys.MENU_PARENT_INVALID);
    }

    @Test
    void deleteRejectsWhenChildrenRemain() {
        when(adminPermissionMapper.selectById(1L)).thenReturn(menu(1L, null, CODE_SYSTEM, "系统", null, 10));
        when(adminPermissionMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.deleteMenu(1L));
        assertThat(ex).isNotNull();
        assertThat(ex.getMessageKey()).isEqualTo(AdminRbacMessageKeys.MENU_HAS_CHILDREN);
    }

    @Test
    void deleteRemovesLeaf() {
        when(adminPermissionMapper.selectById(5L)).thenReturn(menu(5L, null, CODE_CONTENT, "内容", "/content", 20));
        when(adminPermissionMapper.selectCount(any())).thenReturn(0L);

        service.deleteMenu(5L);

        verify(adminPermissionMapper).deleteById(5L);
    }

    private static List<AdminPermissionEntity> sampleMenus() {
        return List.of(
                menu(1L, null, CODE_SYSTEM, "系统", null, 10),
                menu(2L, 1L, CODE_USER, "用户", "/users", 10),
                menu(3L, 1L, CODE_ROLE, "角色", "/roles", 20),
                menu(4L, 3L, CODE_ROLE_DETAIL, "角色详情", "/roles/:id", 10),
                menu(5L, null, CODE_CONTENT, "内容", "/content", 20));
    }

    private static AdminPermissionEntity menu(
            Long id, Long parentId, String code, String name, String routePath, int sort) {
        AdminPermissionEntity entity = new AdminPermissionEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setPermissionCode(code);
        entity.setPermissionName(name);
        entity.setRoutePath(routePath);
        entity.setSortOrder(sort);
        entity.setResourceType("MENU");
        return entity;
    }

    private static AdminCurrentAuthorizationSnapshot snapshot(boolean superAdmin, String... permissions) {
        return new AdminCurrentAuthorizationSnapshot(1L, List.of("editor"), List.of(permissions), superAdmin);
    }

    private static List<String> codes(List<AdminMenuNodeDto> nodes) {
        return nodes.stream().map(AdminMenuNodeDto::menuCode).toList();
    }

    private static AdminMenuNodeDto find(List<AdminMenuNodeDto> nodes, String code) {
        return nodes.stream()
                .filter(node -> code.equals(node.menuCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("menu not found: " + code));
    }
}
