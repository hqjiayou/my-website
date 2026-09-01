package com.mywebsite.site.dto;

import java.time.OffsetDateTime;

public record SiteAiMessageDto(Long id, String role, String content, OffsetDateTime createdAt) {}
