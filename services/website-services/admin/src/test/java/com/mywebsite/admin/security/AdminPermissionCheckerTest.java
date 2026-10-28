package com.mywebsite.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminRbacTestSupport;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@Sql(scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminPermissionCheckerTest extends AbstractAdminSpringBootTest {

    private static final String PERMISSION_RBAC_CHECK = "admin:rbac:check";

    @Autowired
    private AdminPermissionChecker permissionChecker;

    @AfterEach
    void tearDown() {
        AdminRbacTestSupport.clearRequestContext();
    }

    @Test
    void grantsPermissionWhenUserHasIt() {
        AdminRbacTestSupport.bindAdminRequestContext(100L);
        assertThat(permissionChecker.hasPermission("admin:role:list")).isTrue();
        assertThat(permissionChecker.hasPermission("admin:permission:list")).isFalse();
    }

    @Test
    void superAdminPassesAnyPermissionCheck() {
        AdminRbacTestSupport.bindAdminRequestContext(200L);
        assertThat(permissionChecker.hasPermission("admin:role:list")).isTrue();
        assertThat(permissionChecker.hasPermission("admin:not:exists")).isTrue();
        assertThat(permissionChecker.hasRole(AdminRbacConstants.ROLE_SUPER_ADMIN)).isTrue();
    }

    @Test
    void returnsFalseWithoutLoginContext() {
        assertThat(permissionChecker.hasPermission(PERMISSION_RBAC_CHECK)).isFalse();
        assertThat(permissionChecker.hasRole("editor")).isFalse();
    }

    @Test
    void returnsFalseForNonAdminAudience() {
        AdminRbacTestSupport.bindAdminRequestContext(100L);
        AuthContext.set(new AuthenticatedUser(
                TokenAudience.AGENT, SubjectCodes.formatAgentUserSubject(1L), null, 1L, Set.of()));
        assertThat(permissionChecker.hasPermission(PERMISSION_RBAC_CHECK)).isFalse();
    }

    @Test
    void hasAnyPermissionMatchesOneOfCodes() {
        AdminRbacTestSupport.bindAdminRequestContext(100L);
        assertThat(permissionChecker.hasAnyPermission(java.util.List.of("admin:role:list", PERMISSION_RBAC_CHECK)))
                .isTrue();
    }
}
