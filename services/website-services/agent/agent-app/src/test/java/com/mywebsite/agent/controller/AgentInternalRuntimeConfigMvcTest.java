package com.mywebsite.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 运行配置拉取端点（Issue #146 UoW-1）：InternalServiceTokenFilter 鉴权
 * （未带 token 回 401，该 filter 现行实现的状态码）、有 token 返回解密明文、
 * 无配置行/config_version=0 回 configured=false。
 * 夹具行用 JdbcTemplate 直写（绕开单行表的 id 语义），密文经容器内 cipher 生成。
 */
@AutoConfigureMockMvc
class AgentInternalRuntimeConfigMvcTest extends AbstractAgentSpringBootTest {

    private static final String PLAIN_DSN = "postgresql://user:secretpw@db.internal:5432/retrieval";
    private static final String PLAIN_KEY = "AIzaSy-plain-key-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ModelEntryCipher cipher;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM agent_runtime_config");
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/internal/agent/v1/runtime-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        mockMvc.perform(get("/internal/agent/v1/runtime-config")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noConfigRowReturnsConfiguredFalse() throws Exception {
        mockMvc.perform(get("/internal/agent/v1/runtime-config")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.configVersion").value(0))
                .andExpect(jsonPath("$.data.retrievalDatabaseUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void zeroVersionRowReturnsConfiguredFalse() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO agent_runtime_config (id, config_version) VALUES (1, 0)");

        mockMvc.perform(get("/internal/agent/v1/runtime-config")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));
    }

    @Test
    void configuredRowReturnsDecryptedValues() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO agent_runtime_config"
                        + " (id, retrieval_database_url, ai_embedding_model, ai_embedding_dimensions,"
                        + " gemini_api_key, config_version) VALUES (1, ?, ?, ?, ?, 5)",
                cipher.encrypt(PLAIN_DSN),
                "google:gemini-embedding-001",
                768,
                cipher.encrypt(PLAIN_KEY));

        mockMvc.perform(get("/internal/agent/v1/runtime-config")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.retrievalDatabaseUrl").value(PLAIN_DSN))
                .andExpect(jsonPath("$.data.aiEmbeddingModel").value("google:gemini-embedding-001"))
                .andExpect(jsonPath("$.data.aiEmbeddingDimensions").value(768))
                .andExpect(jsonPath("$.data.geminiApiKey").value(PLAIN_KEY))
                .andExpect(jsonPath("$.data.configVersion").value(5));
    }
}
