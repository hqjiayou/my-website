package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentKnowledgeDocumentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;
import java.time.OffsetDateTime;

/** agent-services 业务工具回调（Phase 4）；业务真相源保持在 Java。 */
public interface AgentInternalService {

    AgentSummaryDto getAgentSummary(long agentId);

    AgentContentListDto listPublishedContents(long agentId, String type, int limit);

    /**
     * 枚举某 agent 的知识文档元数据（Issue #181，只 ACTIVE、不含正文）。
     * keyword 为标题包含匹配；format 按 source_format 等值（服务层 trim＋小写归一）；
     * createdFrom/createdTo 按 created_at 含下界/含上界；limit/offset 的夹值口径在实现层定死。
     */
    AgentKnowledgeDocumentListDto listKnowledgeDocuments(
            long agentId,
            String keyword,
            String format,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int limit,
            int offset);
}
