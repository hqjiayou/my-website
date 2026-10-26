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
        List<AgentAttachmentDto> attachments,
        List<String> memories,
        Boolean memoryEnabled,
        // 入口备援链（不含 model 本人）；主用运行期 API 错误时由 Python 顺链切换。无链时传 null，报文与旧版一致。
        List<AgentModelSelection> fallbackModels) {

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
            List<String> knowledgeContext,
            List<AgentAttachmentDto> attachments) {
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
                attachments,
                null,
                null,
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
