package com.mywebsite.site.service;

import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.site.dto.SiteAiConversationDto;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.entity.SiteAiConversationEntity;
import java.util.List;

/** AI 会话真相源（Phase 2）：会话/消息/Prompt 配置/Todo 快照都在 Java + PostgreSQL；Python stateless。 */
public interface SiteAiConversationService {

    /** 组装一次 chat 所需上下文：会话（校验归属或新建）、历史、站点 Prompt 配置、上轮 Todo 快照。 */
    ChatPreparation prepare(long siteId, long siteUserId, String conversationIdOrNull);

    /**
     * 一轮成功交互后持久化 USER/ASSISTANT 消息、Todo 快照（整体覆盖）并刷新会话元数据；
     * {@code runMeta} 写入 ASSISTANT 消息 agent_meta（仅后台观测）。无附件。
     */
    void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta);

    /**
     * 同上，并把 {@code userAttachments} 写入 USER 消息。null 或空表示本轮无图。ASSISTANT 不写附件。
     * 不写 citations。
     */
    void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments);

    /**
     * 同上。{@code citations} 非空时写入 ASSISTANT {@code agent_meta} 的 {@code citations} 字段，并保留已有 run meta。
     * null 或空列表不改变 {@code agent_meta}：空 run meta 仍为 null，非空 run meta 仍只写观测 JSON。
     */
    void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments,
            List<AgentCitation> citations);

    List<SiteAiConversationDto> listConversations(long siteId, long siteUserId);

    List<SiteAiMessageDto> listMessages(long siteId, long siteUserId, long conversationId);

    /** 按 (siteId, siteUserId) 作用域重命名；title 由实现 trim 后写回。不存在或非本人为 NOT_FOUND。 */
    SiteAiConversationDto renameConversation(long siteId, long siteUserId, long conversationId, String title);

    /**
     * 硬删本人会话。不存在或非本人为 NOT_FOUND，且不删除任何行。
     * {@code site_ai_message.conversation_id} 为 ON DELETE RESTRICT，须先删消息再删会话。
     */
    void deleteConversation(long siteId, long siteUserId, long conversationId);

    /**
     * 硬删该会话中 {@code fromMessageId} 及其之后全部消息（供前端「编辑消息」与「重新生成」做真截断，
     * 避免被截断的旧轮次在刷新后复活、或进入后续历史）。会话或目标消息不存在/非本人为 NOT_FOUND；
     * 截断后刷新 {@code lastMessageAt} 为剩余最新消息时间（无剩余时回退会话创建时间），
     * 并清空 Todo 快照（它属于被截断的较新轮次，下一轮会重写）。
     */
    void truncateMessages(long siteId, long siteUserId, long conversationId, long fromMessageId);

    record ChatPreparation(
            SiteAiConversationEntity conversation,
            List<AgentChatHistoryMessage> history,
            AgentPromptProfile promptProfile,
            List<AgentTodoItem> todos) {}
}
