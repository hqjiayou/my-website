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
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentConfigPresetUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentKindUpdateRequest;
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
        mockMvc.perform(get("/api/admin/agents")).andExpect(status().isUnauthorized());
    }

    @Test
    void listSitesForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/agents").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListDetailUpdateStatusDeleteFlow() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "s" + Long.toHexString(System.nanoTime());
        String createBody = MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                agentCode, "Demo Agent", "CAR_REPAIR", "demo.example.com", "desc", null));

        MvcResult createResult = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentCode").value(agentCode))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long id = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/agents").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("agentType", "CAR_REPAIR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(agentCode)).exists())
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        mockMvc.perform(get("/api/admin/agents/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("Demo Agent"))
                .andExpect(jsonPath("$.data.description").value("desc"));

        String updateBody = MAPPER.writeValueAsString(
                new AdminAgentUpdateRequest("Demo Agent Renamed", "SALON", null, "updated", null, 0));
        mockMvc.perform(put("/api/admin/agents/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("Demo Agent Renamed"))
                .andExpect(jsonPath("$.data.agentType").value("SALON"))
                .andExpect(jsonPath("$.data.version").value(1));

        String statusBody = MAPPER.writeValueAsString(new AdminAgentStatusUpdateRequest("DISABLED", 1));
        mockMvc.perform(patch("/api/admin/agents/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(delete("/api/admin/agents/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/agents/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/agents").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.agentCode == '%s')]".formatted(agentCode)).doesNotExist());
    }

    @Test
    void createDuplicateAgentCodeReturnsConflict() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "dup_site_" + Long.toHexString(System.nanoTime());
        String body = MAPPER.writeValueAsString(
                new AdminAgentCreateRequest(agentCode, "One", "CAKE", null, null, null));

        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticConflictOnStaleVersion() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "olv_" + Long.toHexString(System.nanoTime());
        MvcResult createResult = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(agentCode, "OLV", "CLINIC", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data");
        long id = data.path("id").asLong();

        mockMvc.perform(put("/api/admin/agents/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentUpdateRequest("First", "CLINIC", null, null, null, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/admin/agents/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentUpdateRequest("Stale", "CLINIC", null, null, null, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void validationRejectsInvalidAgentCodeAndType() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest("BadCode", "n", "CAR_REPAIR", null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/agents")
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

        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(presetCode, "Preset", "PERSONAL", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentType").value("PERSONAL"));

        mockMvc.perform(post("/api/admin/agents")
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
        mockMvc.perform(post("/api/admin/agents")
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
        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest("no_perm_site", "n", "CAKE", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/agents/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFilterByStatus() throws Exception {
        String token = tokenFor(500L);
        String activeCode = "act_" + Long.toHexString(System.nanoTime());
        String disabledCode = "dis_" + Long.toHexString(System.nanoTime());

        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(disabledCode, "Dis", "USED_CAR", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long disabledId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(activeCode, "Act", "USED_CAR", null, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/agents/" + disabledId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentStatusUpdateRequest("DISABLED", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/agents")
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
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode, "First", "GYOSEISHOSHI", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long id = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/agents/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/agents")
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
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
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

        mockMvc.perform(delete("/api/admin/agents/" + agentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
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
    void deleteSiteCascadesToUsersMemoriesAndShares() throws Exception {
        String token = tokenFor(500L);
        long agentId = createAgent(token, "cascade_a");
        long otherAgentId = createAgent(token, "cascade_b");

        // 入口已平台化（#210 UoW-6）：三条入口（含带历史 agent_id 的存量行）删站后都应保留；
        // 级联只动成员/记忆/分享等 agent 自有数据。
        jdbcTemplate.update(
                "INSERT INTO agent_model_entry (id, display_name, model_spec, api_key, agent_id) VALUES (910001, 'scoped', 'openai:x', 'enc:k1', ?)",
                agentId);
        jdbcTemplate.update(
                "INSERT INTO agent_model_entry (id, display_name, model_spec, api_key, agent_id) VALUES (910002, 'global', 'openai:y', 'enc:k2', NULL)");
        jdbcTemplate.update(
                "INSERT INTO agent_model_entry (id, display_name, model_spec, api_key, agent_id) VALUES (910003, 'other', 'openai:z', 'enc:k3', ?)",
                otherAgentId);
        // 成员、长期记忆、会话与分享各一行（本站）；他站成员一行作对照。
        jdbcTemplate.update(
                "INSERT INTO agent_user (id, agent_id, username, status) VALUES (910011, ?, 'member_a', 'ACTIVE')",
                agentId);
        jdbcTemplate.update(
                "INSERT INTO agent_user (id, agent_id, username, status) VALUES (910012, ?, 'member_b', 'ACTIVE')",
                otherAgentId);
        jdbcTemplate.update(
                "INSERT INTO agent_user_memory (id, agent_id, user_id, content) VALUES (910021, ?, 910011, 'likes tea')",
                agentId);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_conversation (id, agent_id, agent_user_id, title) VALUES (910031, ?, 910011, 'chat')",
                agentId);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_conversation_share (id, conversation_id, agent_id, agent_user_id, token, snapshot)"
                        + " VALUES (910041, 910031, ?, 910011, 'tok-cascade', '{}')",
                agentId);

        mockMvc.perform(delete("/api/admin/agents/" + agentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertSoftDeleted("agent_user", 910011L);
        assertSoftDeleted("agent_user_memory", 910021L);
        assertSoftDeleted("agent_ai_conversation_share", 910041L);
        assertStillActive("agent_model_entry", 910001L);
        assertStillActive("agent_model_entry", 910002L);
        assertStillActive("agent_model_entry", 910003L);
        assertStillActive("agent_user", 910012L);
    }

    private long createAgent(String token, String codePrefix) throws Exception {
        String agentCode = codePrefix + "_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentCreateRequest(
                                agentCode, "Cascade", "GYOSEISHOSHI", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void assertSoftDeleted(String table, long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM " + table + " WHERE id = ?", id);
        assertThat(row.get("deleted")).as(table + " id=" + id).isEqualTo(true);
        assertThat(row.get("deleted_at")).as(table + " id=" + id).isNotNull();
    }

    private void assertStillActive(String table, long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted FROM " + table + " WHERE id = ?", id);
        assertThat(row.get("deleted")).as(table + " id=" + id).isEqualTo(false);
    }

    @Test
    void kindEndpointDefaultsToSiteAndValidatesEnumAndVersion() throws Exception {
        String token = tokenFor(500L);
        long id = createAgent(token, "kind_ep");

        // 创建缺省 kind=SITE，详情带出 agentKind 与 chainPresetId 两列（#213）。
        mockMvc.perform(get("/api/admin/agents/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentKind").value("SITE"));

        mockMvc.perform(put("/api/admin/agents/" + id + "/kind")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentKindUpdateRequest("AI_ONLY", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentKind").value("AI_ONLY"))
                .andExpect(jsonPath("$.data.version").value(1));

        // 封闭枚举外的值 → 400。
        mockMvc.perform(put("/api/admin/agents/" + id + "/kind")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentKindUpdateRequest("HYBRID", 1))))
                .andExpect(status().isBadRequest());

        // version 过期 → 409（乐观锁口径同 status 端点）。
        mockMvc.perform(put("/api/admin/agents/" + id + "/kind")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentKindUpdateRequest("SITE", 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void configPresetEndpointSetsClearsAndRejectsMissingPreset() throws Exception {
        String token = tokenFor(500L);
        long id = createAgent(token, "cfg_ep");
        jdbcTemplate.update("INSERT INTO agent_ai_config_preset (id, name) VALUES (920001, '预设甲')");

        mockMvc.perform(put("/api/admin/agents/" + id + "/config-preset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentConfigPresetUpdateRequest(920001L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configPresetId").value(920001));

        // 预设不存在 → 404（与链预设改引用的 requirePreset 同口径）。
        mockMvc.perform(put("/api/admin/agents/" + id + "/config-preset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentConfigPresetUpdateRequest(920404L))))
                .andExpect(status().isNotFound());

        // null 即清除引用：直查落库值断言，不依赖 JSON null 序列化口径。
        mockMvc.perform(put("/api/admin/agents/" + id + "/config-preset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminAgentConfigPresetUpdateRequest(null))))
                .andExpect(status().isOk());
        Object cleared = jdbcTemplate.queryForObject(
                "SELECT config_preset_id FROM agent WHERE id = ?", Object.class, id);
        assertThat(cleared).isNull();
    }

    @Test
    void listItemsCarryAssociationSummary() throws Exception {
        String token = tokenFor(500L);
        String agentCode = "sum_ep_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminAgentCreateRequest(agentCode, "Summary", "GYOSEISHOSHI", null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long id = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        jdbcTemplate.update("INSERT INTO agent_ai_chain_preset (id, name) VALUES (920011, '链甲')");
        jdbcTemplate.update("INSERT INTO agent_ai_config_preset (id, name) VALUES (920012, '预设乙')");
        jdbcTemplate.update(
                "UPDATE agent SET chain_preset_id = 920011, config_preset_id = 920012 WHERE id = ?", id);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base (id, created_by_agent_id, name) VALUES (920021, ?, '空间一')",
                id);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base (id, created_by_agent_id, name) VALUES (920022, ?, '空间二')",
                id);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base_subscription (base_id, agent_id) VALUES (920021, ?)", id);
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base_subscription (base_id, agent_id) VALUES (920022, ?)", id);

        // 关联摘要：kind 缺省 SITE、订阅数＝订阅表行数（2，含创建者行口径）、两类预设名解析出。
        mockMvc.perform(get("/api/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("agentType", "GYOSEISHOSHI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                                ("$.data.records[?(@.agentCode == '%s' && @.agentKind == 'SITE'"
                                        + " && @.subscriptionCount == 2"
                                        + " && @.chainPresetId == 920011 && @.chainPresetName == '链甲'"
                                        + " && @.configPresetId == 920012 && @.configPresetName == '预设乙')]")
                                        .formatted(agentCode))
                        .exists());
    }

    @Test
    void emptyListReturnsPageShape() throws Exception {
        mockMvc.perform(get("/api/admin/agents").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(500L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
