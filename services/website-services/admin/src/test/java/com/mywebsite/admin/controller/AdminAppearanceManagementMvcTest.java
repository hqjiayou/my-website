package com.mywebsite.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAppearanceManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** H2 seed：japanese-car-repair-v1 / CAR_REPAIR / ENABLED */
    private static final long ENABLED_CAR_TEMPLATE_ID = 1L;

    /** H2 seed：used-car-shop-v1 / USED_CAR / DRAFT */
    private static final long DRAFT_USED_CAR_TEMPLATE_ID = 2L;

    /** H2 seed：cake-shop-v1 / CAKE / ENABLED */
    private static final long ENABLED_CAKE_TEMPLATE_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void createWithTemplateAndAppearance_thenGetAppearance_workflow() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "wf_" + Long.toHexString(System.nanoTime());
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", siteCode);
        body.put("siteName", "Workflow Site");
        body.put("siteType", "CAR_REPAIR");
        body.put("templateId", ENABLED_CAR_TEMPLATE_ID);
        body.set(
                "appearance",
                MAPPER.valueToTree(new AdminAppearanceConfigV1Dto(
                        "#B91C1C", "modern", "rounded", "left-text-right-visual")));

        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteCode").value(siteCode))
                .andExpect(jsonPath("$.data.templateId").value((int) ENABLED_CAR_TEMPLATE_ID))
                .andReturn();

        long siteId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.config.primaryColor").value("#B91C1C"))
                .andExpect(jsonPath("$.data.config.fontStyle").value("modern"))
                .andExpect(jsonPath("$.data.config.buttonStyle").value("rounded"))
                .andExpect(jsonPath("$.data.config.heroLayout").value("left-text-right-visual"));

        Map<String, Object> put = new LinkedHashMap<>();
        put.put("schemaVersion", 1);
        put.put("version", 0);
        put.put(
                "config",
                new AdminAppearanceConfigV1Dto("#2563EB", "classic", "pill", "centered"));

        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(put)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.config.buttonStyle").value("pill"))
                .andExpect(jsonPath("$.data.config.heroLayout").value("centered"));
    }

    @Test
    void coreOnlyCreate_getAppearanceReturns404() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "core_" + Long.toHexString(System.nanoTime());
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", siteCode);
        body.put("siteName", "Core Only");
        body.put("siteType", "CAKE");

        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detail = MAPPER.readTree(created.getResponse().getContentAsString()).path("data");
        long siteId = detail.path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(detail.path("templateId").isNull()).isTrue();

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectUnknownConfigKey() throws Exception {
        String token = tokenFor(500L);
        long siteId = createCoreSite(token, "CAKE");

        String body =
                """
                {"schemaVersion":1,"version":0,"config":{
                  "primaryColor":"#2563EB","fontStyle":"modern","buttonStyle":"rounded",
                  "heroLayout":"centered","logo":"x.png"}}
                """;
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectInvalidJsonBody() throws Exception {
        String token = tokenFor(500L);
        long siteId = createCoreSite(token, "CAKE");
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectWrongSchemaVersion() throws Exception {
        String token = tokenFor(500L);
        long siteId = createCoreSite(token, "CAKE");
        Map<String, Object> put = new LinkedHashMap<>();
        put.put("schemaVersion", 2);
        put.put("version", 0);
        put.put(
                "config",
                new AdminAppearanceConfigV1Dto("#2563EB", "modern", "rounded", "centered"));
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(put)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectInvalidEnumAndColor() throws Exception {
        String token = tokenFor(500L);
        long siteId = createCoreSite(token, "CAKE");
        Map<String, Object> put = new LinkedHashMap<>();
        put.put("schemaVersion", 1);
        put.put("version", 0);
        put.put(
                "config",
                new AdminAppearanceConfigV1Dto("#GG0000", "modern", "rounded", "centered"));
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(put)))
                .andExpect(status().isBadRequest());

        put.put(
                "config",
                new AdminAppearanceConfigV1Dto("#2563EB", "comic", "rounded", "centered"));
        mockMvc.perform(put("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(put)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsTemplateSiteTypeMismatchAndNonEnabled() throws Exception {
        String token = tokenFor(500L);

        // 模板与站点标签已解耦：类型不一致不再拒绝（仅未启用模板仍拒绝，见下方 draft 用例）。
        ObjectNode mismatch = MAPPER.createObjectNode();
        mismatch.put("siteCode", "mm_" + Long.toHexString(System.nanoTime()));
        mismatch.put("siteName", "Mismatch");
        mismatch.put("siteType", "CAR_REPAIR");
        mismatch.put("templateId", ENABLED_CAKE_TEMPLATE_ID);
        mismatch.set(
                "appearance",
                MAPPER.valueToTree(new AdminAppearanceConfigV1Dto(
                        "#B91C1C", "modern", "rounded", "centered")));
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(mismatch)))
                .andExpect(status().isOk());

        ObjectNode draft = MAPPER.createObjectNode();
        draft.put("siteCode", "dr_" + Long.toHexString(System.nanoTime()));
        draft.put("siteName", "DraftTpl");
        draft.put("siteType", "USED_CAR");
        draft.put("templateId", DRAFT_USED_CAR_TEMPLATE_ID);
        draft.set(
                "appearance",
                MAPPER.valueToTree(new AdminAppearanceConfigV1Dto(
                        "#1D4ED8", "modern", "rounded", "centered")));
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(draft)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsPartialTemplateAppearancePair() throws Exception {
        String token = tokenFor(500L);
        ObjectNode onlyTemplate = MAPPER.createObjectNode();
        onlyTemplate.put("siteCode", "pt_" + Long.toHexString(System.nanoTime()));
        onlyTemplate.put("siteName", "Partial");
        onlyTemplate.put("siteType", "CAR_REPAIR");
        onlyTemplate.put("templateId", ENABLED_CAR_TEMPLATE_ID);
        mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(onlyTemplate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void softDeleteSiteAlsoSoftDeletesAppearance() throws Exception {
        String token = tokenFor(500L);
        String siteCode = "sd_" + Long.toHexString(System.nanoTime());
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", siteCode);
        body.put("siteName", "SoftDel");
        body.put("siteType", "CAR_REPAIR");
        body.put("templateId", ENABLED_CAR_TEMPLATE_ID);
        body.set(
                "appearance",
                MAPPER.valueToTree(new AdminAppearanceConfigV1Dto(
                        "#B91C1C", "soft", "square", "full-bleed")));

        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        long siteId = MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/sites/" + siteId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sites/" + siteId + "/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void appearanceForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/sites/1/appearance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    private long createCoreSite(String token, String siteType) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("siteCode", "c_" + Long.toHexString(System.nanoTime()));
        body.put("siteName", "Core");
        body.put("siteType", siteType);
        MvcResult created = mockMvc.perform(post("/api/admin/sites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}
