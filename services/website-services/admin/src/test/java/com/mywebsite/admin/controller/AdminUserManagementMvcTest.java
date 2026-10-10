package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserCreateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserUpdateRequest;
import com.mywebsite.admin.service.AdminUserManagementService;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminUserManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserManagementService userManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void listUsersWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void listUsersForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsersOkForAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[?(@.username == 'admin_mgr')]").exists())
                .andExpect(jsonPath("$.data[?(@.username == 'admin_mgr')].userCode", hasItem("ADMIN_000500")))
                .andExpect(jsonPath("$.data[?(@.username == 'admin_mgr')].protectedUser", hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.username == 'super_user')].userCode", hasItem("ADMIN_000200")))
                .andExpect(jsonPath("$.data[?(@.username == 'super_user')].protectedUser", hasItem(true)))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void getUserDetailNeverReturnsPasswordHash() throws Exception {
        mockMvc.perform(get("/api/admin/users/100").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("editor_user"))
                .andExpect(jsonPath("$.data.userCode").value("ADMIN_000100"))
                .andExpect(jsonPath("$.data.protectedUser").value(false))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void getSuperAdminDetailMarksProtectedUser() throws Exception {
        mockMvc.perform(get("/api/admin/users/200").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("super_user"))
                .andExpect(jsonPath("$.data.userCode").value("ADMIN_000200"))
                .andExpect(jsonPath("$.data.protectedUser").value(true));
    }

    @Test
    void createUserSuccessWithoutRoleBindingAndWithoutPasswordHash() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminUserCreateRequest("new_ops", "password99", "New Ops", "ops@example.com", null));
        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("new_ops"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.displayName").value("New Ops"))
                .andExpect(jsonPath("$.data.protectedUser").value(false))
                .andExpect(jsonPath("$.data.userCode").value(org.hamcrest.Matchers.matchesPattern("ADMIN_\\d{6}")))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andReturn();

        long createdId = MAPPER.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(get("/api/admin/users/" + createdId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isEmpty());
    }

    @Test
    void createUserDuplicateUsernameReturnsConflict() throws Exception {
        AdminUserCreateRequest request =
                new AdminUserCreateRequest("dup_user_crud", "password99", null, null, null);
        userManagementService.createUser(request);
        String body = MAPPER.writeValueAsString(request);
        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createUserForbiddenWithoutPermission() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminUserCreateRequest("no_perm_user", "password99", null, null, null));
        mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserProfileOk() throws Exception {
        String body = MAPPER.writeValueAsString(
                new AdminUserUpdateRequest("Editor Renamed", "editor@example.com", "13800000000"));
        mockMvc.perform(put("/api/admin/users/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Editor Renamed"))
                .andExpect(jsonPath("$.data.email").value("editor@example.com"))
                .andExpect(jsonPath("$.data.username").value("editor_user"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void updateSuperAdminUserFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserUpdateRequest("Nope", null, null));
        mockMvc.perform(put("/api/admin/users/200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableSelfFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserStatusUpdateRequest("DISABLED"));
        mockMvc.perform(patch("/api/admin/users/500/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSelfFails() throws Exception {
        mockMvc.perform(delete("/api/admin/users/500").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableSuperAdminFails() throws Exception {
        String body = MAPPER.writeValueAsString(new AdminUserStatusUpdateRequest("DISABLED"));
        mockMvc.perform(patch("/api/admin/users/200/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSuperAdminFails() throws Exception {
        mockMvc.perform(delete("/api/admin/users/200").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatusAndDeleteOrdinaryUserOk() throws Exception {
        var created = userManagementService.createUser(
                new AdminUserCreateRequest("to_disable", "password99", "Tmp", null, null));
        String token = tokenFor(500L);

        mockMvc.perform(patch("/api/admin/users/" + created.id() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminUserStatusUpdateRequest("DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(delete("/api/admin/users/" + created.id()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users/" + created.id()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM admin_user WHERE id = ?", created.id());
        assertThat(row.get("deleted")).isEqualTo(true);
        assertThat(row.get("deleted_at")).isNotNull();
    }

    @Test
    void authMeIncludesUserManagementPermissionsForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:user:list")))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:user:create")))
                .andExpect(jsonPath("$.data.permissions", hasItem("admin:user:status")));
    }
}
