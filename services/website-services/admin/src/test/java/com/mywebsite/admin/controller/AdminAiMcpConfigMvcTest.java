package com.mywebsite.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import com.mywebsite.admin.testsupport.AdminTestJwtSigner;
import com.mywebsite.agent.client.AgentClient;
import com.jayway.jsonpath.JsonPath;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
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
 * MCP 注册表端点（Issue #192 UoW-1）：写操作须 ai-model:update；GET/PUT 响应
 * 只回 headers/env/url 的 hint、永不含明文；密钥留空保留；推送失败不回滚保存。
 * 推送经 #146 同一管线（retryPush），通道以 @MockBean AgentClient 固定结果。
 */
@AutoConfigureMockMvc
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminAiMcpConfigMvcTest extends AbstractAdminSpringBootTest {

    private static final String SECRET_HEADER = "Bearer topsecret-token-9876";
    private static final String SECRET_URL = "https://user:secretpw@mcp.internal:8443/sse";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentClient agentClient;

    private String tokenFor(long userId) throws Exception {
        RSAKey pk = AdminTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        return AdminTestJwtSigner.signAdminAccess(pk, "test-issuer", "ADMIN", userId);
    }

    /**
     * configVersion 是与运行配置共用的单行计数器（#146 管线），同一测试 JVM 内
     * 其他测试类的保存也会推进它——断言只能相对取基线，不能写死绝对值。
     */
    private long currentConfigVersion(String token) throws Exception {
        String body = mockMvc.perform(get("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number version = JsonPath.read(body, "$.data.configVersion");
        return version.longValue();
    }

    private String createBody() {
        return "{\"servers\":[{\"name\":\"docs\",\"transport\":\"sse\",\"url\":\"" + SECRET_URL + "\","
                + "\"headers\":[{\"key\":\"Authorization\",\"value\":\"" + SECRET_HEADER + "\"}]}]}";
    }

    @Test
    void putThenGetReturnsHintsOnly() throws Exception {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(1L));
        String token = tokenFor(500L);
        long baseVersion = currentConfigVersion(token);

        mockMvc.perform(put("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.servers[0].name").value("docs"))
                .andExpect(jsonPath("$.data.servers[0].urlHint").value("…/sse"))
                .andExpect(jsonPath("$.data.servers[0].headers[0].valueHint").value("…9876"))
                .andExpect(jsonPath("$.data.lastPushStatus").value("APPLIED"))
                .andExpect(content().string(not(containsString("topsecret"))))
                .andExpect(content().string(not(containsString("secretpw"))));

        mockMvc.perform(get("/api/admin/ai/mcp-config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.servers[0].headers[0].configured").value(true))
                .andExpect(jsonPath("$.data.configVersion").value((int) (baseVersion + 1)))
                .andExpect(content().string(not(containsString("topsecret"))))
                .andExpect(content().string(not(containsString("secretpw"))));
    }

    @Test
    void putWithBlankSecretKeepsExistingValue() throws Exception {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(1L));
        String token = tokenFor(500L);
        long baseVersion = currentConfigVersion(token);

        mockMvc.perform(put("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isOk());

        String keepBody = "{\"servers\":[{\"name\":\"docs\",\"transport\":\"sse\","
                + "\"headers\":[{\"key\":\"Authorization\"}]}]}";
        mockMvc.perform(put("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keepBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.servers[0].headers[0].valueHint").value("…9876"))
                .andExpect(jsonPath("$.data.servers[0].urlHint").value("…/sse"))
                .andExpect(jsonPath("$.data.configVersion").value((int) (baseVersion + 2)));
    }

    @Test
    void pushFailureDoesNotRollBackSave() throws Exception {
        when(agentClient.pushRuntimeConfig(any()))
                .thenReturn(AgentRuntimeConfigPushResult.failed("agent-services unreachable"));
        String token = tokenFor(500L);

        mockMvc.perform(put("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.servers[0].name").value("docs"))
                .andExpect(jsonPath("$.data.lastPushStatus").value("FAILED"));

        mockMvc.perform(get("/api/admin/ai/mcp-config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.servers[0].headers[0].valueHint").value("…9876"));
    }

    @Test
    void putForbiddenWithoutUpdatePermission() throws Exception {
        String token = tokenFor(100L);

        mockMvc.perform(put("/api/admin/ai/mcp-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }
}
