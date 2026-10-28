package com.mywebsite.agent.dto;

import java.util.List;

public record AgentAiChatRequestDto(
        String message,
        String conversationId,
        List<AgentAiAttachmentDto> attachments,
        List<AgentAiDocumentDto> documents) {

    public AgentAiChatRequestDto(String message, String conversationId, List<AgentAiAttachmentDto> attachments) {
        this(message, conversationId, attachments, null);
    }
}
