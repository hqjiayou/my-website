package com.mywebsite.site.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SiteAiMessageDto(
        Long id, String role, String content, OffsetDateTime createdAt, List<SiteAiAttachmentDto> attachments) {}
