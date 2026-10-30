package com.mywebsite.agent.dto;

import java.util.List;

public record AgentAiChatRequestDto(
        String message,
        String conversationId,
        List<AgentAiAttachmentDto> attachments,
        List<AgentAiDocumentDto> documents,
        /** 双模式切换：KEY / CLI 必填；空或非法值拒绝（不再有整链混搭口径，Issue #145 F4c）。 */
        String mode,
        /**
         * 这一轮点名的入口。空则仍用该模式的默认入口。只影响这一轮，不改默认入口。
         * 不在该模式生效链上时拒绝，不改走默认（Issue #153）。
         */
        Long modelEntryId) {

    public AgentAiChatRequestDto(String message, String conversationId, List<AgentAiAttachmentDto> attachments) {
        this(message, conversationId, attachments, null, null, null);
    }

    public AgentAiChatRequestDto(
            String message, String conversationId, List<AgentAiAttachmentDto> attachments, List<AgentAiDocumentDto> documents) {
        this(message, conversationId, attachments, documents, null, null);
    }

    public AgentAiChatRequestDto(
            String message,
            String conversationId,
            List<AgentAiAttachmentDto> attachments,
            List<AgentAiDocumentDto> documents,
            String mode) {
        this(message, conversationId, attachments, documents, mode, null);
    }
}
