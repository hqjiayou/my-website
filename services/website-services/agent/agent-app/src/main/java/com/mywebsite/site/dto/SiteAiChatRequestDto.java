package com.mywebsite.site.dto;

import java.util.List;

public record SiteAiChatRequestDto(
        String message,
        String conversationId,
        List<SiteAiAttachmentDto> attachments,
        List<SiteAiDocumentDto> documents) {

    public SiteAiChatRequestDto(String message, String conversationId, List<SiteAiAttachmentDto> attachments) {
        this(message, conversationId, attachments, null);
    }
}
