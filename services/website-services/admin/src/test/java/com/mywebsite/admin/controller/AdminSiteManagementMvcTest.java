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
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteCreateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteUpdateRequest;
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
class AdminSiteManagementMvcTest extends AbstractAdminSpringBootTest {

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
        String siteCode = "s" + Long.toHexString(System.nanoTime());
        String createBody = MAPPER.writeValueAsString(new AdminSiteCreateRequest(
                siteCode, "Demo Site", "CAR_REPAIR", "demo.example.com", "desc", null, null));

        MvcResult createResult = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteCode").value(siteCode))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long id = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("siteType", "CAR_REPAIR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.siteCode == '%s')]".formatted(siteCode)).exists())
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        mockMvc.perform(get("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("Demo Site"))
                .andExpect(jsonPath("$.data.description").value("desc"));

        String updateBody = MAPPER.writeValueAsString(
                new AdminSiteUpdateRequest("Demo Site Renamed", "SALON", null, "updated", 0));
        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("Demo Site Renamed"))
                .andExpect(jsonPath("$.data.siteType").value("SALON"))
                .andExpect(jsonPath("$.data.version").value(1));

        String statusBody = MAPPER.writeValueAsString(new AdminSiteStatusUpdateRequest("DISABLED", 1));
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
                .andExpect(jsonPath("$.data.records[?(@.siteCode == '%s')]".formatted(siteCode)).doesNotExist());
    }

    @Test
    void createDuplicateSiteCodeReturnsConflict() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "dup_site_" + Long.toHexString(System.nanoTime());
        String body = MAPPER.writeValueAsString(
                new AdminSiteCreateRequest(siteCode, "One", "CAKE", null, null, null, null));

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
        String siteCode = "olv_" + Long.toHexString(System.nanoTime());
        MvcResult createResult = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest(siteCode, "OLV", "CLINIC", null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = MAPPER.readTree(createResult.getResponse().getContentAsString()).path("data");
        long id = data.path("id").asLong();

        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteUpdateRequest("First", "CLINIC", null, null, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/admin/sites/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteUpdateRequest("Stale", "CLINIC", null, null, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void validationRejectsInvalidSiteCodeAndType() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest("BadCode", "n", "CAR_REPAIR", null, null, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest("ok_code", "n", "bad type!", null, null, null, null))))
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
                                new AdminSiteCreateRequest(presetCode, "Preset", "PERSONAL", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteType").value("PERSONAL"));

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest(customCode, "Custom", "My-Docs_2", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteType").value("My-Docs_2"));
    }

    @Test
    void createAllowsTemplateWhoseTypeDiffersFromSiteTag() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "tmpl_" + Long.toHexString(System.nanoTime());

        // 模板 id=1 为旧 CAR_REPAIR 模板；站点标签 PERSONAL 与之不同——解耦后应可创建。
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminSiteCreateRequest(
                                siteCode,
                                "Decoupled",
                                "PERSONAL",
                                null,
                                null,
                                1L,
                                new AdminAppearanceConfigV1Dto(
                                        "#B91C1C", "modern", "rounded", "left-text-right-visual")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteType").value("PERSONAL"));
    }

    @Test
    void createForbiddenWithoutCreatePermission() throws Exception {
        // editor 仅有 role/list/detail 等，无 site:create
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest("no_perm_site", "n", "CAKE", null, null, null, null))))
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
                                new AdminSiteCreateRequest(disabledCode, "Dis", "USED_CAR", null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long disabledId =
                MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(
                                new AdminSiteCreateRequest(activeCode, "Act", "USED_CAR", null, null, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/sites/" + disabledId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminSiteStatusUpdateRequest("DISABLED", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("status", "DISABLED")
                        .param("siteType", "USED_CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.siteCode == '%s')]".formatted(disabledCode)).exists())
                .andExpect(jsonPath("$.data.records[?(@.siteCode == '%s')]".formatted(activeCode)).doesNotExist());
    }

    @Test
    void softDeletedSiteCodeCanBeReused() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "reuse_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminSiteCreateRequest(
                                siteCode, "First", "GYOSEISHOSHI", null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        long id = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminSiteCreateRequest(
                                siteCode, "Second", "GYOSEISHOSHI", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteCode").value(siteCode));
    }

    @Test
    void softDeleteSiteWithAppearanceSetsDeletedAt() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "sda_" + Long.toHexString(System.nanoTime());
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminSiteCreateRequest(
                                siteCode,
                                "SoftDelAt",
                                "CAR_REPAIR",
                                null,
                                null,
                                1L,
                                new AdminAppearanceConfigV1Dto(
                                        "#B91C1C", "modern", "rounded", "left-text-right-visual")))))
                .andExpect(status().isOk())
                .andReturn();
        long siteId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + siteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> site = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM site WHERE id = ?", siteId);
        Map<String, Object> appearance = jdbcTemplate.queryForMap(
                "SELECT deleted, deleted_at FROM site_appearance WHERE site_id = ?", siteId);
        assertThat(site.get("deleted")).isEqualTo(true);
        assertThat(site.get("deleted_at")).isNotNull();
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
