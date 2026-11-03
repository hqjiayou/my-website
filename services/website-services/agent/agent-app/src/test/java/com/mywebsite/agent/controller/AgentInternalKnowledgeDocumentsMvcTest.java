package com.mywebsite.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 知识文档元数据枚举端点（Issue #181）：internal token 鉴权、只回 ACTIVE 元数据（不含正文）、
 * keyword/format/时间范围过滤与 limit/offset 分页口径（total/truncated）。
 */
@AutoConfigureMockMvc
class AgentInternalKnowledgeDocumentsMvcTest extends AbstractAgentSpringBootTest {

    private static final String PATH = "/internal/agent/v1/agents/10/knowledge-documents";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long janPayslipId;
    private long febPayslipId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM agent_ai_knowledge_document");
        jdbcTemplate.update("DELETE FROM agent");
        jdbcTemplate.update(
                "INSERT INTO agent (id, agent_name, agent_type, status) VALUES (10, 'Test Agent', 'PERSONAL', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO agent (id, agent_name, agent_type, status) VALUES (11, 'Other Agent', 'PERSONAL', 'ACTIVE')");
        janPayslipId = insertDocument(
                10L, "2026年1月工资单", "正文-1月", "ACTIVE", "xlsx", "payslip-2026-01.xlsx", "INDEXED",
                OffsetDateTime.of(2026, 2, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        febPayslipId = insertDocument(
                10L, "2026年2月工资单", "正文-2月", "ACTIVE", "xlsx", "payslip-2026-02.xlsx", "INDEXED",
                OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        insertDocument(
                10L, "员工手册", "正文-手册", "ACTIVE", "pdf", "handbook.pdf", "PENDING",
                OffsetDateTime.of(2026, 1, 10, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        insertDocument(
                10L, "2025年12月工资单", "不应可见", "DISABLED", "xlsx", "payslip-2025-12.xlsx", "PENDING",
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
        insertDocument(
                11L, "他站工资单", "不应可见", "ACTIVE", "xlsx", "other.xlsx", "INDEXED",
                OffsetDateTime.of(2026, 2, 15, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
    }

    private long insertDocument(
            long agentId,
            String title,
            String content,
            String status,
            String sourceFormat,
            String originalFilename,
            String indexStatus,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO agent_ai_knowledge_document"
                        + " (agent_id, title, content, status, source_format, original_filename, index_status, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                agentId, title, content, status, sourceFormat, originalFilename, indexStatus, createdAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM agent_ai_knowledge_document WHERE title = ?", Long.class, title);
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyOwnActiveDocumentsMetadataWithoutContent() throws Exception {
        mockMvc.perform(get(PATH).header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.limit").value(100))
                .andExpect(jsonPath("$.data.offset").value(0))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // created_at 升序：员工手册（01-10）→ 1月工资单（02-01）→ 2月工资单（03-01）
                .andExpect(jsonPath("$.data.items[0].title").value("员工手册"))
                .andExpect(jsonPath("$.data.items[1].title").value("2026年1月工资单"))
                .andExpect(jsonPath("$.data.items[1].id").value(janPayslipId))
                .andExpect(jsonPath("$.data.items[1].sourceId").value("kb:" + janPayslipId))
                .andExpect(jsonPath("$.data.items[1].sourceFormat").value("xlsx"))
                .andExpect(jsonPath("$.data.items[1].originalFilename").value("payslip-2026-01.xlsx"))
                .andExpect(jsonPath("$.data.items[1].indexStatus").value("INDEXED"))
                .andExpect(jsonPath("$.data.items[1].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[2].id").value(febPayslipId));
    }

    @Test
    void keywordFiltersByTitleSubstring() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("keyword", "工资单")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void formatFiltersBySourceFormatCaseInsensitively() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("format", "PDF")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("员工手册"));
    }

    @Test
    void createdRangeFiltersByCreatedAtInclusive() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("createdFrom", "2026-02-01T00:00:00+09:00")
                        .param("createdTo", "2026-02-28T23:59:59+09:00")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("2026年1月工资单"));
    }

    @Test
    void paginationReportsTruncatedAndPagesThrough() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("limit", "2")
                        .param("offset", "0")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mockMvc.perform(get(PATH)
                        .param("limit", "2")
                        .param("offset", "2")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("2026年2月工资单"));
    }

    @Test
    void unknownAgentIsNotFound() throws Exception {
        mockMvc.perform(get("/internal/agent/v1/agents/999/knowledge-documents")
                        .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, TEST_AGENT_INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
    }
}
