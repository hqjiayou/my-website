package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigDto;
import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigUpdateRequest;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentRuntimeConfigPayload;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.mywebsite.agent.entity.AgentRuntimeConfigEntity;
import com.mywebsite.agent.mapper.AgentRuntimeConfigMapper;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.crypto.ModelEntrySecretProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 运行配置服务（Issue #146 UoW-1）：保存加密、留空保留、版本递增、GET 脱敏、
 * 推送三结果分别落 status、推送失败不回滚保存、状态说明不夹带密钥。
 */
class AdminAiRuntimeConfigServiceImplTest {

    private static final String PLAIN_DSN = "postgresql://user:secretpw@db.internal:5432/retrieval";
    private static final String PLAIN_KEY = "AIzaSy-plain-key-1234";

    private AgentRuntimeConfigMapper mapper;
    private AgentClient agentClient;
    private ModelEntryCipher cipher;
    private AdminAiRuntimeConfigServiceImpl service;
    private AgentRuntimeConfigEntity stored;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentRuntimeConfigMapper.class);
        agentClient = mock(AgentClient.class);
        ModelEntrySecretProperties secret = new ModelEntrySecretProperties();
        secret.setModelEntrySecret("test-secret");
        cipher = new ModelEntryCipher(secret);
        stored = new AgentRuntimeConfigEntity();
        stored.setId(1L);
        stored.setRetrievalDatabaseUrl(cipher.encrypt(PLAIN_DSN));
        stored.setGeminiApiKey(cipher.encrypt(PLAIN_KEY));
        stored.setAiEmbeddingModel("google:gemini-embedding-001");
        stored.setAiEmbeddingDimensions(768);
        stored.setConfigVersion(3L);
        stored.setVersion(0);
        when(mapper.selectById(1L)).thenReturn(stored);
        when(mapper.updateById(any(AgentRuntimeConfigEntity.class))).thenReturn(1);
        when(mapper.insert(any(AgentRuntimeConfigEntity.class))).thenReturn(1);
        service = new AdminAiRuntimeConfigServiceImpl(mapper, cipher, agentClient);
    }

    @Test
    void getConfigMasksDsnAndOnlyHintsKey() {
        AdminAiRuntimeConfigDto dto = service.getConfig();

        assertThat(dto.retrievalDatabaseUrlConfigured()).isTrue();
        assertThat(dto.retrievalDatabaseUrlMasked()).isEqualTo("postgresql://db.internal:5432/retrieval");
        assertThat(dto.retrievalDatabaseUrlMasked()).doesNotContain("secretpw").doesNotContain("user");
        assertThat(dto.geminiApiKeyConfigured()).isTrue();
        assertThat(dto.geminiApiKeyHint()).isEqualTo("…1234");
        assertThat(dto.configVersion()).isEqualTo(3L);
        assertThat(dto.aiEmbeddingModel()).isEqualTo("google:gemini-embedding-001");
        assertThat(dto.aiEmbeddingDimensions()).isEqualTo(768);
    }

    @Test
    void maskDsnDropsUserinfoPortAndQuery() {
        assertThat(AdminAiRuntimeConfigServiceImpl.maskDsn(PLAIN_DSN))
                .isEqualTo("postgresql://db.internal:5432/retrieval");
        assertThat(AdminAiRuntimeConfigServiceImpl.maskDsn("postgresql://user:pw@db.internal/retrieval?sslmode=require"))
                .isEqualTo("postgresql://db.internal/retrieval");
        // 解析不出 host 时只留 scheme 段，绝不回原文片段（可能含口令）。
        assertThat(AdminAiRuntimeConfigServiceImpl.maskDsn("not a uri")).isNull();
        assertThat(AdminAiRuntimeConfigServiceImpl.maskDsn("  ")).isNull();
        assertThat(AdminAiRuntimeConfigServiceImpl.maskDsn(null)).isNull();
    }

    @Test
    void updateEncryptsSecretsIncrementsVersionAndPushesPlaintext() {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(4L));
        String newDsn = "postgresql://u2:p2@db2.internal:6432/otherdb";
        String newKey = "new-gemini-key-9876";

        AdminAiRuntimeConfigDto dto = service.updateConfig(
                new AdminAiRuntimeConfigUpdateRequest(newDsn, " google:gemini-embedding-002 ", 1024, newKey));

        // 落库的是密文，且能解回新明文。
        assertThat(stored.getRetrievalDatabaseUrl()).startsWith("enc:v1:");
        assertThat(cipher.decrypt(stored.getRetrievalDatabaseUrl())).isEqualTo(newDsn);
        assertThat(cipher.decrypt(stored.getGeminiApiKey())).isEqualTo(newKey);
        assertThat(stored.getAiEmbeddingModel()).isEqualTo("google:gemini-embedding-002");
        assertThat(stored.getAiEmbeddingDimensions()).isEqualTo(1024);
        assertThat(stored.getConfigVersion()).isEqualTo(4L);
        // 推送载荷是解密明文＋新版本。
        ArgumentCaptor<AgentRuntimeConfigPayload> payload = ArgumentCaptor.forClass(AgentRuntimeConfigPayload.class);
        verify(agentClient).pushRuntimeConfig(payload.capture());
        assertThat(payload.getValue().retrievalDatabaseUrl()).isEqualTo(newDsn);
        assertThat(payload.getValue().geminiApiKey()).isEqualTo(newKey);
        assertThat(payload.getValue().configVersion()).isEqualTo(4L);
        // 推送结果落状态列，DTO 回 APPLIED。
        assertThat(dto.configVersion()).isEqualTo(4L);
        assertThat(dto.lastPushStatus()).isEqualTo("APPLIED");
        assertThat(dto.lastPushedAt()).isNotNull();
    }

    @Test
    void updateWithBlankSecretsRetainsStoredValues() {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(4L));

        AdminAiRuntimeConfigDto dto =
                service.updateConfig(new AdminAiRuntimeConfigUpdateRequest("", null, null, "  "));

        // 密钥保留原值（仍是原密文、解得出原明文），非密钥字段被清空，版本照样递增。
        assertThat(cipher.decrypt(stored.getRetrievalDatabaseUrl())).isEqualTo(PLAIN_DSN);
        assertThat(cipher.decrypt(stored.getGeminiApiKey())).isEqualTo(PLAIN_KEY);
        assertThat(stored.getAiEmbeddingModel()).isNull();
        assertThat(stored.getAiEmbeddingDimensions()).isNull();
        assertThat(dto.configVersion()).isEqualTo(4L);
        ArgumentCaptor<AgentRuntimeConfigPayload> payload = ArgumentCaptor.forClass(AgentRuntimeConfigPayload.class);
        verify(agentClient).pushRuntimeConfig(payload.capture());
        assertThat(payload.getValue().retrievalDatabaseUrl()).isEqualTo(PLAIN_DSN);
    }

    @Test
    void blockedPushStillKeepsSavedConfigAndRecordsReason() {
        when(agentClient.pushRuntimeConfig(any()))
                .thenReturn(AgentRuntimeConfigPushResult.blocked("store probe failed"));

        AdminAiRuntimeConfigDto dto = service.updateConfig(
                new AdminAiRuntimeConfigUpdateRequest(null, "google:gemini-embedding-009", 768, null));

        // 保存不回滚：新模型与新版本都在；状态记 BLOCKED＋原因。
        assertThat(stored.getAiEmbeddingModel()).isEqualTo("google:gemini-embedding-009");
        assertThat(dto.configVersion()).isEqualTo(4L);
        assertThat(dto.lastPushStatus()).isEqualTo("BLOCKED");
        assertThat(dto.lastPushMessage()).isEqualTo("store probe failed");
    }

    @Test
    void failedPushRecordsFailedStatus() {
        when(agentClient.pushRuntimeConfig(any()))
                .thenReturn(AgentRuntimeConfigPushResult.failed("agent-services unreachable"));

        AdminAiRuntimeConfigDto dto = service.retryPush();

        assertThat(dto.lastPushStatus()).isEqualTo("FAILED");
        assertThat(dto.lastPushMessage()).isEqualTo("agent-services unreachable");
    }

    @Test
    void retryPushDoesNotChangeConfigOrVersion() {
        when(agentClient.pushRuntimeConfig(any())).thenReturn(AgentRuntimeConfigPushResult.applied(3L));

        AdminAiRuntimeConfigDto dto = service.retryPush();

        assertThat(dto.configVersion()).isEqualTo(3L);
        assertThat(stored.getAiEmbeddingModel()).isEqualTo("google:gemini-embedding-001");
        ArgumentCaptor<AgentRuntimeConfigPayload> payload = ArgumentCaptor.forClass(AgentRuntimeConfigPayload.class);
        verify(agentClient).pushRuntimeConfig(payload.capture());
        assertThat(payload.getValue().configVersion()).isEqualTo(3L);
        assertThat(payload.getValue().retrievalDatabaseUrl()).isEqualTo(PLAIN_DSN);
    }

    @Test
    void pushMessageContainingSecretIsWithheld() {
        when(agentClient.pushRuntimeConfig(any()))
                .thenReturn(AgentRuntimeConfigPushResult.blocked("bad dsn " + PLAIN_DSN + " refused"));

        AdminAiRuntimeConfigDto dto = service.retryPush();

        assertThat(dto.lastPushStatus()).isEqualTo("BLOCKED");
        assertThat(dto.lastPushMessage()).doesNotContain("secretpw");
        assertThat(dto.lastPushMessage()).contains("withheld");
    }
}
