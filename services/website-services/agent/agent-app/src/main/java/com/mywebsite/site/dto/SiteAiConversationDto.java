package com.mywebsite.site.dto;

import java.time.OffsetDateTime;

public record SiteAiConversationDto(
        Long id, String title, OffsetDateTime lastMessageAt, OffsetDateTime createdAt) {}
