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
        List<AgentEmbeddingClass> embeddingClasses,
        // 客户端 IANA 时区名。空则执行层回退 UTC。不传时间戳。只用于钟点。
        String timezone,
        // 浏览器或手机定位权限授予后的坐标。不成对则执行层忽略，不从时区推断城市。
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        // 后台试聊标记：只有 admin preview 传 TRUE。执行层据此启用回复内卡片
        // （Issue #179）；正式对话传 null，NON_NULL 序列化下报文与旧版逐字一致。
        Boolean preview,
        // 已批准经验条目（Issue #184 UoW-4）：平台全局，正式对话与试聊每请求
        // 现查现带，执行层组装为 # Experience lessons 段。无条目时传 null，
        // NON_NULL 序列化下报文与旧版逐字一致。
        List<AgentExperienceLessonItem> experienceLessons,
        // MCP per-agent 名字过滤（Issue #192 UoW-3）：三态——null＝不限制
        // （INHERIT，用全局启用集）、空列表＝本 agent 不用 MCP（OFF）、
        // 非空＝只用这些 server 名（CUSTOM，与全局启用集取交集）。
        // 只带名字不带条目本体：密钥不随每轮请求过通道。
        List<String> mcpServerNames) {

    /** 兼容不带 timezone 的构造点。缺省时执行层回退 UTC。 */
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
            List<AgentDocumentContext> documents,
            String embeddingSpec,
            String embeddingApiKey,
            String embeddingBaseUrl,
            List<AgentEmbeddingClass> embeddingClasses) {
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
                embeddingSpec,
                embeddingApiKey,
                embeddingBaseUrl,
                embeddingClasses,
                null,
                null,
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
