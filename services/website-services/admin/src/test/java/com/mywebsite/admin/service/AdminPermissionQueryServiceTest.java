package com.mywebsite.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminRbacTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@Sql(scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminPermissionQueryServiceTest extends AbstractAdminSpringBootTest {

    @Autowired
    private AdminPermissionQueryService permissionQueryService;

    @AfterEach
    void tearDown() {
        AdminRbacTestSupport.clearRequestContext();
    }

    @Test
    void loadsRolesAndPermissionsByUserCode() {
        var auth = permissionQueryService.loadForUserCode("ADMIN_000100");
        assertThat(auth).isPresent();
        assertThat(auth.get().roles()).containsExactly("editor");
    }

    @Test
    void loadsRolesAndPermissionsForEditor() {
        AdminRbacTestSupport.bindAdminRequestContext(100L);

        var auth = permissionQueryService.loadForCurrentUser();
        assertThat(auth).isPresent();
        assertThat(auth.get().roles()).containsExactly("editor");
        assertThat(auth.get().permissions())
                .containsExactlyInAnyOrder(
                        "admin:menu:system", "admin:rbac:check", "admin:user:list", "admin:role:list", "admin:role:detail");
        assertThat(auth.get().superAdmin()).isFalse();
    }

    @Test
    void superAdminReceivesAllActivePermissions() {
        AdminRbacTestSupport.bindAdminRequestContext(200L);

        var auth = permissionQueryService.loadForCurrentUser();
        assertThat(auth).isPresent();
        assertThat(auth.get().roles()).containsExactly(AdminRbacConstants.ROLE_SUPER_ADMIN);
        assertThat(auth.get().superAdmin()).isTrue();
        assertThat(auth.get().permissions()).contains("admin:role:list", "admin:rbac:check");
        assertThat(auth.get().permissions()).doesNotContain("admin:user:delete");
    }

    @Test
    void userWithoutRolesHasEmptyGrants() {
        AdminRbacTestSupport.bindAdminRequestContext(300L);

        var auth = permissionQueryService.loadForCurrentUser();
        assertThat(auth).isPresent();
        assertThat(auth.get().roles()).isEmpty();
        assertThat(auth.get().permissions()).isEmpty();
    }

    @Test
    void disabledRoleDoesNotGrantPermissions() {
        AdminRbacTestSupport.bindAdminRequestContext(400L);

        var auth = permissionQueryService.loadForCurrentUser();
        assertThat(auth).isPresent();
        assertThat(auth.get().roles()).isEmpty();
        assertThat(auth.get().permissions()).isEmpty();
    }

    @Test
    void isSuperAdminReflectsRoleBinding() {
        assertThat(permissionQueryService.isSuperAdmin(200L)).isTrue();
        assertThat(permissionQueryService.isSuperAdmin(100L)).isFalse();
    }
}
