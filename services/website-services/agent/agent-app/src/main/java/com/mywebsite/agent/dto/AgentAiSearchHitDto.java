package com.mywebsite.agent.dto;

import java.time.OffsetDateTime;

/** 跨会话全文搜索的一条命中：所属会话、消息与围绕命中词的摘要片段。 */
public record AgentAiSearchHitDto(
        Long conversationId,
        String conversationTitle,
        Long messageId,
        String role,
        String snippet,
        OffsetDateTime createdAt) {}
