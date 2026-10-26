package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentChatRequest(
        String siteId,
        String message,
        String locale,
        String conversationId,
        AgentPromptProfile promptProfile,
        List<AgentChatHistoryMessage> history,
        List<String> skills,
        List<AgentTodoItem> todos,
        AgentModelSelection model,
        String businessApiBaseUrl,
        List<String> knowledgeContext,
        List<AgentAttachmentDto> attachments) {

    public AgentChatRequest(
            String siteId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history,
            List<String> skills,
            List<AgentTodoItem> todos,
            AgentModelSelection model,
            String businessApiBaseUrl,
            List<String> knowledgeContext) {
        this(
                siteId,
                message,
                locale,
                conversationId,
                promptProfile,
                history,
                skills,
                todos,
                model,
                businessApiBaseUrl,
                knowledgeContext,
                null);
    }

    public AgentChatRequest(
            String siteId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history,
            List<String> skills,
            List<AgentTodoItem> todos,
            AgentModelSelection model) {
        this(siteId, message, locale, conversationId, promptProfile, history, skills, todos, model, null, null);
    }

    public AgentChatRequest(
            String siteId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history,
            List<String> skills,
            List<AgentTodoItem> todos) {
        this(siteId, message, locale, conversationId, promptProfile, history, skills, todos, null);
    }

    public AgentChatRequest(String siteId, String message) {
        this(siteId, message, null, null, null, null, null, null);
    }

    public AgentChatRequest(
            String siteId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile) {
        this(siteId, message, locale, conversationId, promptProfile, null, null, null);
    }

    public AgentChatRequest(
            String siteId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history) {
        this(siteId, message, locale, conversationId, promptProfile, history, null, null);
    }
}
