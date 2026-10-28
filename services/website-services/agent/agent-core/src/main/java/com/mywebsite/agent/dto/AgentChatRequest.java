package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentChatRequest(
        String agentId,
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
        List<AgentModelSelection> fallbackModels,
        // 本轮文档上下文（站点层已抽取文本）；无文档时传 null，报文与旧版一致。
        List<AgentDocumentContext> documents) {

    public AgentChatRequest(
            String agentId,
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
            List<AgentModelSelection> fallbackModels) {
        this(
                agentId,
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
                memories,
                memoryEnabled,
                fallbackModels,
                null);
    }

    public AgentChatRequest(
            String agentId,
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
                agentId,
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
                null,
                null);
    }

    public AgentChatRequest(
            String agentId,
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
                agentId,
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
            String agentId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history,
            List<String> skills,
            List<AgentTodoItem> todos,
            AgentModelSelection model) {
        this(agentId, message, locale, conversationId, promptProfile, history, skills, todos, model, null, null);
    }

    public AgentChatRequest(
            String agentId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history,
            List<String> skills,
            List<AgentTodoItem> todos) {
        this(agentId, message, locale, conversationId, promptProfile, history, skills, todos, null);
    }

    public AgentChatRequest(String agentId, String message) {
        this(agentId, message, null, null, null, null, null, null);
    }

    public AgentChatRequest(
            String agentId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile) {
        this(agentId, message, locale, conversationId, promptProfile, null, null, null);
    }

    public AgentChatRequest(
            String agentId,
            String message,
            String locale,
            String conversationId,
            AgentPromptProfile promptProfile,
            List<AgentChatHistoryMessage> history) {
        this(agentId, message, locale, conversationId, promptProfile, history, null, null);
    }
}
