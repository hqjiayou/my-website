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
        List<AgentDocumentContext> documents,
        // 向量索引凭证，与回答模型分开。两种对话模式共用；都空时 Python 用进程嵌入模型。
        String embeddingSpec,
        String embeddingApiKey,
        String embeddingBaseUrl,
        // 已登记的嵌入类。有值时执行层按类检索；不传时仍只用上面那一套。
        List<AgentEmbeddingClass> embeddingClasses) {

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
            List<AgentModelSelection> fallbackModels,
            List<AgentDocumentContext> documents) {
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
                documents,
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
                null,
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
