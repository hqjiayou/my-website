package com.mywebsite.admin.dto;

import com.mywebsite.agent.dto.AgentRunMeta;
import java.time.OffsetDateTime;

public final class AdminAiConversationDtos {

    private AdminAiConversationDtos() {}

    /** {@code source}：PORTAL | ADMIN_PREVIEW；预览会话的 {@code agentUserId} 为 null。 */
    public record AdminAiConversationListItemDto(
            Long id,
            Long agentId,
            Long agentUserId,
            String source,
            String title,
            OffsetDateTime lastMessageAt,
            OffsetDateTime createdAt) {}

    /** {@code agentMeta}：ASSISTANT 消息的运行元数据（用量/工具轨迹/skills），无或格式异常时为 null。 */
    public record AdminAiMessageDto(
            Long id, String role, String content, OffsetDateTime createdAt, AgentRunMeta agentMeta) {}

    public record AdminAiConversationListQuery(Integer pageNum, Integer pageSize) {}
}
