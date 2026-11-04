package com.mywebsite.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 运行配置端点（Issue #146 UoW-1）：写操作须 ai-model:update（user 100 的角色只有
 * 部分读权限、无 update）、GET/PUT 响应只回 DSN 脱敏形与 key hint、永不含明文。
 * 推送通道以 @MockBean AgentClient 固定回 APPLIED，通道本身的契约在 HttpAgentClientTest 覆盖。
 */
@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAiRuntimeConfigMvcTest extends AbstractAdminSpringBootTest {

    private static final String DSN = "postgresql://user:secretpw@db.internal:5432/retrieval";
    private static final String GEMINI_KEY = "AIzaSy-plain-key-1234";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentClient agentClient;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    private long currentConfigVersion(String token) throws Exception {
        String body = mockMvc.perform(get("/api/admin/ai/runtime-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number version = JsonPath.read(body, "$.data.configVersion");
        return version.longValue();
    }

    private String updateBody() {
        return "{\"retrievalDatabaseUrl\":\"" + DSN + "\",\"aiEmbeddingModel\":\"google:gemini-embedding-001\","
                + "\"aiEmbeddingDimensions\":768,\"geminiApiKey\":\"" + GEMINI_KEY + "\"}";
    }

    @Test
    void putThenGetReturnsMaskedDsnAndHintOnly() throws Exception {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(1L));
        String token = tokenFor(500L);
        long baseVersion = currentConfigVersion(token);

        mockMvc.perform(put("/api/admin/ai/runtime-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrievalDatabaseUrlMasked").value("postgresql://db.internal:5432/retrieval"))
                .andExpect(jsonPath("$.data.geminiApiKeyHint").value("…1234"))
                .andExpect(jsonPath("$.data.lastPushStatus").value("APPLIED"))
                .andExpect(content().string(not(containsString("secretpw"))))
                .andExpect(content().string(not(containsString(GEMINI_KEY))));

        mockMvc.perform(get("/api/admin/ai/runtime-config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrievalDatabaseUrlConfigured").value(true))
                .andExpect(jsonPath("$.data.retrievalDatabaseUrlMasked").value("postgresql://db.internal:5432/retrieval"))
                .andExpect(jsonPath("$.data.geminiApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.configVersion").value((int) (baseVersion + 1)))
                .andExpect(content().string(not(containsString("secretpw"))))
                .andExpect(content().string(not(containsString(GEMINI_KEY))));
    }

    @Test
    void putForbiddenWithoutUpdatePermission() throws Exception {
        String token = tokenFor(100L);

        mockMvc.perform(put("/api/admin/ai/runtime-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryPushRecordsStatusWithoutConfigChange() throws Exception {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(1L));
        String token = tokenFor(500L);

        mockMvc.perform(post("/api/admin/ai/runtime-config/retry-push")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastPushStatus").value("APPLIED"));
    }
}
