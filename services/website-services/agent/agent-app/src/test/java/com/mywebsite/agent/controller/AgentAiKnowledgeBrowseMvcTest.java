package com.mywebsite.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.agent.testsupport.AgentTestJwtSigner;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 站点用户只读浏览知识库文档（列表/详情）：成员校验、只见 ACTIVE 与可达空间
 * （创建∪订阅，Issue #209 UoW-4）内的文档；文档 agent_id 停写后按 base 归属判定。
 */
@AutoConfigureMockMvc
class AgentAiKnowledgeBrowseMvcTest extends AbstractAgentSpringBootTest {

    private static final long OWN_BASE_ID = 100L;
    private static final long SUBSCRIBED_BASE_ID = 200L;
    private static final long FOREIGN_BASE_ID = 300L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RSAKey privateKey;
    private long activeDocId;

    @BeforeEach
    void setUp() throws Exception {
        privateKey = AgentTestJwtSigner.loadRsaFromClasspathPrivatePem("jwt/test-private.pem");
        jdbcTemplate.update("DELETE FROM agent_ai_knowledge_document");
        jdbcTemplate.update("DELETE FROM agent_ai_knowledge_base_subscription");
        jdbcTemplate.update("DELETE FROM agent_ai_knowledge_base");
        insertBase(OWN_BASE_ID, 10L);
        insertBase(FOREIGN_BASE_ID, 11L);
        insertSubscription(OWN_BASE_ID, 10L);
        insertSubscription(FOREIGN_BASE_ID, 11L);
        // 文档 agent_id 一律 NULL：UoW-1 停写后的新文档形状，可见性只看 base_id。
        activeDocId = insertDocument(OWN_BASE_ID, "公开手册", "# 手册正文", "ACTIVE");
        insertDocument(OWN_BASE_ID, "已停用文档", "不应可见", "DISABLED");
        insertDocument(FOREIGN_BASE_ID, "他站文档", "不应可见", "ACTIVE");
    }

    private void insertBase(long baseId, long createdByAgentId) {
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base (id, created_by_agent_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                baseId, createdByAgentId, "空间-" + baseId);
    }

    private void insertSubscription(long baseId, long agentId) {
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_base_subscription (base_id, agent_id) VALUES (?, ?)",
                baseId, agentId);
    }

    private long insertDocument(long baseId, String title, String content, String status) {
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_document (base_id, title, content, status) VALUES (?, ?, ?, ?)",
                baseId,
                title,
                content,
                status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM agent_ai_knowledge_document WHERE title = ?", Long.class, title);
    }

    private String token() throws Exception {
        return AgentTestJwtSigner.signAgentAccess(privateKey, "test-issuer", "AGENT", 200L, 10L);
    }

    @Test
    void listReturnsOnlyOwnAgentActiveDocumentsWithoutContent() throws Exception {
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("公开手册"))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());
    }

    @Test
    void detailReturnsMarkdownContent() throws Exception {
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents/" + activeDocId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("公开手册"))
                .andExpect(jsonPath("$.data.content").value("# 手册正文"));
    }

    @Test
    void detailOfDisabledOrForeignDocumentIsNotFound() throws Exception {
        Long disabledId = jdbcTemplate.queryForObject(
                "SELECT id FROM agent_ai_knowledge_document WHERE title = '已停用文档'", Long.class);
        Long foreignId = jdbcTemplate.queryForObject(
                "SELECT id FROM agent_ai_knowledge_document WHERE title = '他站文档'", Long.class);
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents/" + disabledId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents/" + foreignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribedSpaceDocumentsAreBrowsable() throws Exception {
        insertBase(SUBSCRIBED_BASE_ID, 11L);
        insertSubscription(SUBSCRIBED_BASE_ID, 11L);
        insertSubscription(SUBSCRIBED_BASE_ID, 10L);
        long sharedDocId = insertDocument(SUBSCRIBED_BASE_ID, "共享手册", "# 共享正文", "ACTIVE");
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents/" + sharedDocId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("共享手册"))
                .andExpect(jsonPath("$.data.content").value("# 共享正文"));
    }

    @Test
    void listWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/agent/10/ai/knowledge-documents")).andExpect(status().isUnauthorized());
    }
}
