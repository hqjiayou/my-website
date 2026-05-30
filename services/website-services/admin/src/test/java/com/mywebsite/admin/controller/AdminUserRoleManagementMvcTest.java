package com.mywebsite.admin.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminUserRoleUpdateRequest;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Sql(scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminUserRoleManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void getUserRolesWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/users/100/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void getUserRolesForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/users/300/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserRolesOkWithProtectedFlag() throws Exception {
        mockMvc.perform(get("/api/admin/users/200/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCode").value("ADMIN_000200"))
                .andExpect(jsonPath("$.data.roles[?(@.roleCode == 'super_admin')].protectedRole").value(true));

        mockMvc.perform(get("/api/admin/users/100/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("editor"))
                .andExpect(jsonPath("$.data.roles[0].protectedRole").value(false));
    }

    @Test
    void getUserRolesNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/users/999/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaceUserRolesSuccess() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("editor")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("editor"));

        mockMvc.perform(get("/api/admin/users/300/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("editor"));
    }

    @Test
    void replaceWithUnknownRoleCodeFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("no_such_role")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceWithDisabledRoleFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("disabled_role")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceWithEmptyRoleCodesFails() throws Exception {
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"roleCodes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceSelfRolesFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("admin")));
        mockMvc.perform(put("/api/admin/users/500/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceSuperAdminUserFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("admin")));
        mockMvc.perform(put("/api/admin/users/200/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceWithSuperAdminRoleCodeFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("super_admin")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceForbiddenWithoutUpdatePermission() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("editor")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void authMeReflectsUpdatedRolesWithoutNewJwt() throws Exception {
        String updateBody = MAPPER.writeValueAsString(new AdminUserRoleUpdateRequest(List.of("admin")));
        mockMvc.perform(put("/api/admin/users/300/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("admin"))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:user:role:view")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("admin:rbac:check"))))
                .andExpect(jsonPath("$.data.menus").isEmpty());
    }
}
