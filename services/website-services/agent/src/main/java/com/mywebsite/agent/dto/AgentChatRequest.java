package com.mywebsite.agent.dto;

public record AgentChatRequest(
        String siteId,
        String message,
        String locale,
        String conversationId,
        AgentPromptProfile promptProfile) {

    public AgentChatRequest(String siteId, String message) {
        this(siteId, message, null, null, null);
    }
}
