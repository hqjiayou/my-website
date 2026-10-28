package com.mywebsite.agent.dto;

import java.util.List;

public record AgentAiChatRequestDto(
        String message,
        String conversationId,
        List<AgentAiAttachmentDto> attachments,
        List<AgentAiDocumentDto> documents,
        /** 双模式切换：KEY / CLI；空 = 不分模式（整链，兼容旧客户端）。 */
        String mode) {

    public AgentAiChatRequestDto(String message, String conversationId, List<AgentAiAttachmentDto> attachments) {
        this(message, conversationId, attachments, null, null);
    }

    public AgentAiChatRequestDto(
            String message, String conversationId, List<AgentAiAttachmentDto> attachments, List<AgentAiDocumentDto> documents) {
        this(message, conversationId, attachments, documents, null);
    }
}
