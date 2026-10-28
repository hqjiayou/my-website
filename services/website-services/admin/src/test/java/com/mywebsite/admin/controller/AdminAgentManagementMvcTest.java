package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
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
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAgentManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void listSitesWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/sites")).andExpect(status().isUnauthorized());
    }

    @Test
    void listSitesForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListDetailUpdateStatusDeleteFlow() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "s" + Long.toHexString(System.nanoTime());
        String createBody = MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                agentCode, "Demo Agent", "CAR_REPAIR", "demo.example.com", "desc", null));

        MvcResult createResult = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentCode").value(agentCode))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long id = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("agentType", "CAR_REPAIR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(agentCode)).exists())
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        mockMvc.perform(get("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("Demo Agent"))
                .andExpect(jsonPath("$.data.description").value("desc"));

        String updateBody = MAPPER.writeValueAsString(
                new AdminAgentUpdateRequest("Demo Agent Renamed", "SALON", null, "updated", 0));
        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("Demo Agent Renamed"))
                .andExpect(jsonPath("$.data.agentType").value("SALON"))
                .andExpect(jsonPath("$.data.version").value(1));

        String statusBody = MAPPER.writeValueAsString(new AdminAgentStatusUpdateRequest("DISABLED", 1));
        mockMvc.perform(patch("/api/admin/sites/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(delete("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(agentCode)).doesNotExist());
    }

    @Test
    void createDuplicateAgentCodeReturnsConflict() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "dup_site_" + Long.toHexString(System.nanoTime());
        String body = MAPPER.writeValueAsString(
                new AdminAgentCreateRequest(agentCode, "One", "CAKE", null, null, null));

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticConflictOnStaleVersion() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "olv_" + Long.toHexString(System.nanoTime());
        MvcResult createResult = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(agentCode, "OLV", "CLINIC", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data");
        long id = data.path("id").asLong();

        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentUpdateRequest("First", "CLINIC", null, null, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentUpdateRequest("Stale", "CLINIC", null, null, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void validationRejectsInvalidAgentCodeAndType() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest("BadCode", "n", "CAR_REPAIR", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest("ok_code", "n", "bad type!", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAcceptsPresetAndCustomTags() throws Exception {
        String token = tokenFor(500L);
        String presetCode = "tag_p_" + Long.toHexString(System.nanoTime());
        String customCode = "tag_c_" + Long.toHexString(System.nanoTime());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(presetCode, "Preset", "PERSONAL", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentType").value("PERSONAL"));

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(customCode, "Custom", "My-Docs_2", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentType").value("My-Docs_2"));
    }

    @Test
    void createWithAppearanceAndCustomTag() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "app_" + Long.toHexString(System.nanoTime());

        // 模板下架后 appearance 独立给出即可创建，与站点标签无关。
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode,
                                "Decoupled",
                                "PERSONAL",
                                null,
                                null,
                                new AdminAppearanceConfigV1Dto(
                                        "#B91C1C", "modern", "rounded", "left-text-right-visual")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentType").value("PERSONAL"));
    }

    @Test
    void createForbiddenWithoutCreatePermission() throws Exception {
        // editor 仅有 role/list/detail 等，无 agent:create
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest("no_perm_site", "n", "CAKE", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/sites/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFilterByStatus() throws Exception {
        String token = tokenFor(500L);
        String activeCode = "act_" + Long.toHexString(System.nanoTime());
        String disabledCode = "dis_" + Long.toHexString(System.nanoTime());

        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(disabledCode, "Dis", "USED_CAR", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long disabledId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(activeCode, "Act", "USED_CAR", null, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/sites/" + disabledId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentStatusUpdateRequest("DISABLED", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("status", "DISABLED")
                        .param("agentType", "USED_CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(disabledCode)).exists())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(activeCode)).doesNotExist());
    }

    @Test
    void softDeletedAgentCodeCanBeReused() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "reuse_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode, "First", "GYOSEISHOSHI", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long id = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode, "Second", "GYOSEISHOSHI", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentCode").value(agentCode));
    }

    @Test
    void softDeleteAgentWithAppearanceSetsDeletedAt() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "sda_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode,
                                "SoftDelAt",
                                "CAR_REPAIR",
                                null,
                                null,
                                new AdminAppearanceConfigV1Dto(
                                        "#B91C1C", "modern", "rounded", "left-text-right-visual")))))
                .andExpect(status().isOk())
                .andReturn();
        long agentId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + agentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> agent = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM agent WHERE id = ?", agentId);
        Map<String, Object> appearance = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM site_appearance WHERE site_id = ?", agentId);
        assertThat(agent.get("deleted")).isEqualTo(true);
        assertThat(agent.get("deleted_at")).isNotNull();
        assertThat(appearance.get("deleted")).isEqualTo(true);
        assertThat(appearance.get("deleted_at")).isNotNull();
    }

    @Test
    void emptyListReturnsPageShape() throws Exception {
        mockMvc.perform(get("/api/admin/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
