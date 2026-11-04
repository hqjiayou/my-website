package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.McpServerEntryInput;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.SecretKvInput;
import com.mywebsite.admin.service.AdminAiRuntimeConfigService;
import com.mywebsite.agent.entity.AgentRuntimeConfigEntity;
import com.mywebsite.agent.mapper.AgentRuntimeConfigMapper;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.crypto.ModelEntrySecretProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 注册表服务（Issue #192 UoW-1）：整份 JSON 加密落库、版本递增、推送走
 * #146 同一管线、GET 脱敏（headers/env/url 只回 hint）、密钥留空保留与
 * clear 删键、重名拒绝。
 */
class AdminAiMcpConfigServiceImplTest {

    private static final String SECRET_HEADER = "Bearer topsecret-token-9876";
    private static final String SECRET_ENV = "env-secret-value-5432";
    private static final String SECRET_URL = "https://user:secretpw@mcp.internal:8443/sse";

    private AgentRuntimeConfigMapper mapper;
    private AdminAiRuntimeConfigService runtimeConfigService;
    private ModelEntryCipher cipher;
    private AdminAiMcpConfigServiceImpl service;
    private AgentRuntimeConfigEntity stored;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(AgentRuntimeConfigMapper.class);
        runtimeConfigService = mock(AdminAiRuntimeConfigService.class);
        ModelEntrySecretProperties secret = new ModelEntrySecretProperties();
        secret.setModelEntrySecret("test-secret");
        cipher = new ModelEntryCipher(secret);
        stored = new AgentRuntimeConfigEntity();
        stored.setId(1L);
        stored.setConfigVersion(3L);
        stored.setVersion(0);
        stored.setMcpServersJson(cipher.encrypt(
                "[{\"name\":\"docs\",\"transport\":\"sse\",\"url\":\"" + SECRET_URL + "\","
                        + "\"headers\":{\"Authorization\":\"" + SECRET_HEADER + "\"},"
                        + "\"env\":{\"API_KEY\":\"" + SECRET_ENV + "\"}}]"));
        when(mapper.selectById(1L)).thenReturn(stored);
        when(mapper.updateById(any(AgentRuntimeConfigEntity.class))).thenReturn(1);
        service = new AdminAiMcpConfigServiceImpl(mapper, cipher, runtimeConfigService, objectMapper);
    }

    private JsonNode storedEntries() throws Exception {
        return objectMapper.readTree(cipher.decrypt(stored.getMcpServersJson()));
    }

    @Test
    void getConfigReturnsHintsOnlyNeverPlaintext() {
        AdminAiMcpConfigDto dto = service.getConfig();

        assertThat(dto.servers()).hasSize(1);
        var entry = dto.servers().get(0);
        assertThat(entry.name()).isEqualTo("docs");
        assertThat(entry.urlConfigured()).isTrue();
        assertThat(entry.urlHint()).isEqualTo("…/sse");
        assertThat(entry.headers()).hasSize(1);
        assertThat(entry.headers().get(0).key()).isEqualTo("Authorization");
        assertThat(entry.headers().get(0).valueHint()).isEqualTo("…9876");
        assertThat(entry.env().get(0).valueHint()).isEqualTo("…5432");
        assertThat(dto.toString())
                .doesNotContain("topsecret")
                .doesNotContain("secretpw")
                .doesNotContain("env-secret");
        assertThat(dto.configVersion()).isEqualTo(3L);
    }

    @Test
    void updateKeepsSecretsOnBlankAndBumpsVersionAndPushes() throws Exception {
        AdminAiMcpConfigDto dto = service.updateConfig(new AdminAiMcpConfigUpdateRequest(List.of(
                new McpServerEntryInput(
                        "docs", "sse", null, true, null, null, null, null, null,
                        List.of(new SecretKvInput("Authorization", null, null)),
                        List.of(new SecretKvInput("API_KEY", "  ", null))))));

        JsonNode entries = storedEntries();
        assertThat(entries.get(0).path("headers").path("Authorization").asText()).isEqualTo(SECRET_HEADER);
        assertThat(entries.get(0).path("env").path("API_KEY").asText()).isEqualTo(SECRET_ENV);
        assertThat(entries.get(0).path("url").asText()).isEqualTo(SECRET_URL);
        assertThat(stored.getConfigVersion()).isEqualTo(4L);
        verify(runtimeConfigService).retryPush();
        assertThat(dto.configVersion()).isEqualTo(4L);
    }

    @Test
    void updateReplacesSecretValueAndClearRemovesKey() throws Exception {
        service.updateConfig(new AdminAiMcpConfigUpdateRequest(List.of(
                new McpServerEntryInput(
                        "docs", "sse", null, true, null, null, null, null, null,
                        List.of(new SecretKvInput("Authorization", "Bearer new-token-1111", null)),
                        List.of(new SecretKvInput("API_KEY", null, true))))));

        JsonNode entries = storedEntries();
        assertThat(entries.get(0).path("headers").path("Authorization").asText())
                .isEqualTo("Bearer new-token-1111");
        assertThat(entries.get(0).has("env")).isFalse();
    }

    @Test
    void updateWithEmptyListClearsRegistryToEmptyArrayNotNull() throws Exception {
        service.updateConfig(new AdminAiMcpConfigUpdateRequest(List.of()));

        assertThat(cipher.decrypt(stored.getMcpServersJson())).isEqualTo("[]");
        assertThat(service.getConfig().servers()).isEmpty();
    }

    @Test
    void updateRejectsDuplicateNames() {
        McpServerEntryInput entry = new McpServerEntryInput(
                "docs", "http", "https://a.example", true, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateConfig(new AdminAiMcpConfigUpdateRequest(List.of(entry, entry))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateAddsNewStdioEntryWithArgs() throws Exception {
        service.updateConfig(new AdminAiMcpConfigUpdateRequest(List.of(
                new McpServerEntryInput(
                        "local-echo", "stdio", null, true, null, null,
                        "python", List.of("-m", "echo"), "/tmp", null,
                        List.of(new SecretKvInput("TOKEN", "stdio-secret-2222", null))))));

        JsonNode entries = storedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).path("command").asText()).isEqualTo("python");
        assertThat(entries.get(0).path("args").get(1).asText()).isEqualTo("echo");
        assertThat(entries.get(0).path("env").path("TOKEN").asText()).isEqualTo("stdio-secret-2222");
    }
}
