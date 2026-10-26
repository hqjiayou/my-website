package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import com.mywebsite.admin.dto.AdminTemplateDtos.AdminTemplateStatusUpdateRequest;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminTemplateManagementMvcTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    @Test
    void listTemplatesWithoutBearerIs401() throws Exception {
        mockMvc.perform(get("/api/admin/templates")).andExpect(status().isUnauthorized());
    }

    @Test
    void listTemplatesForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/templates").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(300L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDetailByCodeAndStatusFlow() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(get("/api/admin/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.records[?(@.code == 'japanese-car-repair-v1')]").exists());

        mockMvc.perform(get("/api/admin/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("siteType", "CAKE")
                        .param("status", "ENABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.code == 'cake-shop-v1')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.code == 'japanese-car-repair-v1')]").doesNotExist());

        MvcResult detailResult = mockMvc.perform(get("/api/admin/templates/by-code/japanese-car-repair-v1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("japanese-car-repair-v1"))
                .andExpect(jsonPath("$.data.siteType").value("CAR_REPAIR"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.defaultPrimaryColor").value("#B91C1C"))
                .andReturn();

        long id = MAPPER.readTree(detailResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/templates/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("japanese-car-repair-v1"));

        String statusBody = MAPPER.writeValueAsString(new AdminTemplateStatusUpdateRequest("DISABLED", 0));
        mockMvc.perform(patch("/api/admin/templates/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void optimisticConflictOnStaleVersion() throws Exception {
        String token = tokenFor(500L);
        MvcResult detail = mockMvc.perform(get("/api/admin/templates/by-code/salon-v1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = MAPPER.readTree(detail.getResponse().getContentAsString()).path("data");
        long id = data.path("id").asLong();

        mockMvc.perform(patch("/api/admin/templates/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminTemplateStatusUpdateRequest("ENABLED", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(patch("/api/admin/templates/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminTemplateStatusUpdateRequest("DRAFT", 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidStatusAndSiteTypeRejected() throws Exception {
        String token = tokenFor(500L);

        mockMvc.perform(get("/api/admin/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isBadRequest());

        // siteType 已改自由标签：未知标签不再 400，而是过滤后返回空列表。
        mockMvc.perform(get("/api/admin/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("siteType", "NOT_A_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty());

        MvcResult detail = mockMvc.perform(get("/api/admin/templates/by-code/clinic-v1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long id = MAPPER.readTree(detail.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/admin/templates/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminTemplateStatusUpdateRequest("enabled", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailAndByCodeNotFound() throws Exception {
        String token = tokenFor(500L);
        mockMvc.perform(get("/api/admin/templates/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/templates/by-code/missing-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(patch("/api/admin/templates/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(100L))
                        .contentType(APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(new AdminTemplateStatusUpdateRequest("ENABLED", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void contentWriteEndpointsAreNotExposed() throws Exception {
        String token = tokenFor(500L);
        // GlobalExceptionHandler 将 MethodNotSupported 记为未处理 → 500；断言方法不被支持即可。
        mockMvc.perform(post("/api/admin/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(HttpRequestMethodNotSupportedException.class));
        mockMvc.perform(put("/api/admin/templates/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(HttpRequestMethodNotSupportedException.class));
        mockMvc.perform(delete("/api/admin/templates/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(HttpRequestMethodNotSupportedException.class));
    }
}
