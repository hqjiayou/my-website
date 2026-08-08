package com.mywebsite.admin.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.admin.testsupport.RbacTestController;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(RbacTestController.class)
@Sql(scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AdminRbacSecurityMvcTest extends AbstractAdminSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rbacCheckWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/test/rbac/check").header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void rbacCheckForbiddenWithoutPermission() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 300L);

        mockMvc.perform(get("/api/admin/test/rbac/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.message").value("アクセスが拒否されました"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rbacCheckOkWithPermission() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/admin/test/rbac/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void rbacCheckOkForSuperAdminWithoutExplicitBinding() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 200L);

        mockMvc.perform(get("/api/admin/test/rbac/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void authMeReturnsRealRolesAndPermissions() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.user.userCode").value("ADMIN_000100"))
                .andExpect(jsonPath("$.data.roles[0]").value("editor"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:rbac:check")))
                .andExpect(jsonPath("$.data.menus", hasSize(1)))
                .andExpect(jsonPath("$.data.menus[0].menuCode").value("admin:menu:system"))
                .andExpect(jsonPath("$.data.menus[0].children", empty()));
    }

    @Test
    void authMeSuperAdminMenusContainSystemTree() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 200L);

        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menus", hasSize(2)))
                .andExpect(jsonPath("$.data.menus[0].menuCode").value("admin:menu:system"))
                .andExpect(jsonPath("$.data.menus[0].children", hasSize(4)))
                .andExpect(jsonPath("$.data.menus[0].children[0].menuCode").value("admin:menu:system.user"))
                .andExpect(jsonPath("$.data.menus[0].children[0].path").value("/system/users"))
                .andExpect(jsonPath("$.data.menus[1].menuCode").value("admin:menu:apps"))
                .andExpect(jsonPath("$.data.menus[1].children", hasSize(2)))
                .andExpect(jsonPath("$.data.menus[1].children[0].path").value("/apps/sites"))
                .andExpect(jsonPath("$.data.menus[1].children[1].path").value("/apps/templates"));
    }

    @Test
    void listMenusRequiresPermission() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 100L);

        mockMvc.perform(get("/api/admin/menus").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMenusOkForAdminRole() throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        String token = AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", 500L);

        mockMvc.perform(get("/api/admin/menus").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].menuCode").value("admin:menu:system"))
                .andExpect(jsonPath("$.data[0].children", hasSize(4)))
                .andExpect(jsonPath("$.data[1].menuCode").value("admin:menu:apps"))
                .andExpect(jsonPath("$.data[1].children", hasSize(2)))
                .andExpect(jsonPath("$.data[1].children[0].path").value("/apps/sites"))
                .andExpect(jsonPath("$.data[1].children[1].path").value("/apps/templates"));
    }
}
