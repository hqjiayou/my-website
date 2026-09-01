package com.mywebsite.admin.dto;

import java.time.OffsetDateTime;

public final class AdminAiConversationDtos {

    private AdminAiConversationDtos() {}

    public record AdminAiConversationListItemDto(
            Long id,
            Long siteId,
            Long siteUserId,
            String title,
            OffsetDateTime lastMessageAt,
            OffsetDateTime createdAt) {}

    public record AdminAiMessageDto(Long id, String role, String content, OffsetDateTime createdAt) {}

    public record AdminAiConversationListQuery(Integer pageNum, Integer pageSize) {}
}
