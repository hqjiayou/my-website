package com.mywebsite.agent.dto;

import java.util.List;

/** agent-services 业务工具回调专用 DTO（internal API，非公开契约）。 */
public final class AgentInternalDtos {

    private AgentInternalDtos() {}

    public record AgentSummaryDto(Long id, String agentName, String agentType, String description) {}

    public record AgentContentItemDto(Long id, String type, String title, String body) {}

    public record AgentContentListDto(List<AgentContentItemDto> items) {}

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
