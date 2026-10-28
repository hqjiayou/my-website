package com.mywebsite.agent.dto;

import java.time.OffsetDateTime;

public record AgentAiConversationDto(
        Long id, String title, OffsetDateTime lastMessageAt, OffsetDateTime createdAt) {}
