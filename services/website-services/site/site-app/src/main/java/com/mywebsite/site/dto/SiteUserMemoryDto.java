package com.mywebsite.site.dto;

import java.time.OffsetDateTime;

public record SiteUserMemoryDto(Long id, String content, OffsetDateTime createdAt) {}
