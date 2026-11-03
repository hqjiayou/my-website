package com.mywebsite.agent.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** agent-services 业务工具回调专用 DTO（internal API，非公开契约）。 */
public final class AgentInternalDtos {

    private AgentInternalDtos() {}

    public record AgentSummaryDto(Long id, String agentName, String agentType, String description) {}

    public record AgentContentItemDto(Long id, String type, String title, String body) {}

    public record AgentContentListDto(List<AgentContentItemDto> items) {}

    /**
     * 知识文档枚举单项（Issue #181）：只有元数据、不含正文。
     * {@code sourceId} 与检索片段/结构化表格的来源口径对齐（{@code "kb:" + id}），
     * 供 agent 逐份回指文档。
     */
    public record AgentKnowledgeDocumentItemDto(
            Long id,
            String sourceId,
            String title,
            String sourceFormat,
            String originalFilename,
            String indexStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /**
     * 知识文档枚举响应（Issue #181）：{@code total} 为同过滤条件下的总数，
     * {@code truncated=true} 表示本页之后还有（offset + 本页条数 &lt; total），调用方翻页取全。
     */
    public record AgentKnowledgeDocumentListDto(
            List<AgentKnowledgeDocumentItemDto> items, long total, int limit, int offset, boolean truncated) {}

    /**
     * 运行配置拉取响应（Issue #146 UoW-1）：agent-services 启动时收敛页面配置用。
     * {@code configured=false} 表示页面从未保存过（无行或 config_version=0），四项为 null，
     * 与「拉取失败」（HTTP 错误）严格区分。configured=true 时四项为解密明文
     * （只经 internal token 通道，禁止落日志）。
     */
    public record RuntimeConfigDto(
            boolean configured,
            String retrievalDatabaseUrl,
            String aiEmbeddingModel,
            Integer aiEmbeddingDimensions,
            String geminiApiKey,
            long configVersion) {}
}
