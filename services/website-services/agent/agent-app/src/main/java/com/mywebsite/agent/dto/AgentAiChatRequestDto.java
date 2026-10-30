package com.mywebsite.agent.dto;

import java.util.List;

public record AgentAiChatRequestDto(
        String message,
        String conversationId,
        List<AgentAiAttachmentDto> attachments,
        List<AgentAiDocumentDto> documents,
        /** 双模式切换：KEY / CLI 必填；空或非法值拒绝（不再有整链混搭口径，Issue #145 F4c）。 */
        String mode) {

    public AgentAiChatRequestDto(String message, String conversationId, List<AgentAiAttachmentDto> attachments) {
        this(message, conversationId, attachments, null, null);
    }

    public AgentAiChatRequestDto(
            String message, String conversationId, List<AgentAiAttachmentDto> attachments, List<AgentAiDocumentDto> documents) {
        this(message, conversationId, attachments, documents, null);
    }
}
