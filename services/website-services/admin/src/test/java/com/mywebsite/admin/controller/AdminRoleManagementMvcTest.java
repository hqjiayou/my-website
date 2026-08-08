package com.mywebsite.admin.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleUpdateRequest;
import com.mywebsite.admin.service.AdminRoleManagementService;
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
class AdminRoleManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRoleManagementService roleManagementService;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void listRolesWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void listRolesForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRolesOkForAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[?(@.roleCode == 'super_admin')]").exists());
    }

    @Test
    void createRoleSuccess() throws Exception {
        String token = tokenFor(500L);
        String roleCode = "r" + Long.toHexString(System.nanoTime());
        String body = MAPPER.writeValueAsString(
                new AdminRoleCreateRequest(roleCode, "Operator IT", "desc", "ACTIVE"));
        mockMvc.perform(post("/api/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value(roleCode));
    }

    @Test
    void createRoleDuplicateReturnsConflict() throws Exception {
        String token = tokenFor(500L);
        String roleCode = "dup_role_test";
        AdminRoleCreateRequest request = new AdminRoleCreateRequest(roleCode, "Operator IT", "desc", "ACTIVE");
        roleManagementService.createRole(request);
        String body = MAPPER.writeValueAsString(request);
        mockMvc.perform(post("/api/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCreateSuperAdminRole() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminRoleCreateRequest("super_admin", "Bad", null, "ACTIVE"));
        mockMvc.perform(post("/api/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSuperAdminFails() throws Exception {
        mockMvc.perform(delete("/api/admin/roles/2").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(200L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRoleInUseFails() throws Exception {
        mockMvc.perform(delete("/api/admin/roles/4").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceRolePermissionsAndQuery() throws Exception {
        String token = tokenFor(500L);
        String updateBody = MAPPER.writeValueAsString(
                new AdminRolePermissionsUpdateRequest(List.of("admin:role:list", "admin:permission:list")));

        mockMvc.perform(put("/api/admin/roles/1/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:role:list")));

        mockMvc.perform(get("/api/admin/roles/1/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:permission:list")));
    }

    @Test
    void replaceSuperAdminPermissionsFails() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminRolePermissionsUpdateRequest(List.of("admin:role:list")));
        mockMvc.perform(put("/api/admin/roles/2/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(200L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceWithUnknownPermissionFails() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminRolePermissionsUpdateRequest(List.of("admin:not:exists")));
        mockMvc.perform(put("/api/admin/roles/1/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void permissionsListReadOnly() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'admin:role:list')]").exists());
    }

    @Test
    void authMeShowsSeedPermissionsForAdminUser() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("admin"))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:role:create")))
                .andExpect(jsonPath("$.data.menus", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.menus[0].menuCode").value("admin:menu:system"))
                .andExpect(jsonPath("$.data.menus[0].children", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.data.menus[1].menuCode").value("admin:menu:apps"))
                .andExpect(jsonPath("$.data.menus[1].children[0].menuCode").value("admin:menu:apps.sites"));
    }

    @Test
    void authMeSuperAdminHasAllActivePermissions() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(200L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("super_admin"))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:role:list")))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:permission:list")))
                .andExpect(jsonPath("$.data.menus", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.menus[0].children", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.data.menus[1].menuCode").value("admin:menu:apps"));
    }

    @Test
    void updateRoleOk() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminRoleUpdateRequest("Editor Renamed", "d", "ACTIVE"));
        mockMvc.perform(put("/api/admin/roles/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("Editor Renamed"));
    }
}
