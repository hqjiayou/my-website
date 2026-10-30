package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.agent.dto.AgentAiConversationDto;
import com.mywebsite.agent.dto.AgentAiMessageDto;
import com.mywebsite.agent.dto.AgentAiSearchHitDto;
import com.mywebsite.agent.dto.AgentAiShareDto;
import com.mywebsite.agent.dto.AgentAiSharedConversationDto;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import java.util.List;

/** AI 会话真相源（Phase 2）：会话/消息/Prompt 配置/Todo 快照都在 Java + PostgreSQL；Python stateless。 */
public interface AgentAiConversationService {

    /** 组装一次 chat 所需上下文：会话（校验归属或新建）、历史、站点 Prompt 配置、上轮 Todo 快照。 */
    ChatPreparation prepare(long agentId, long agentUserId, String conversationIdOrNull);

    /**
     * 一轮成功交互后持久化 USER/ASSISTANT 消息、Todo 快照（整体覆盖）并刷新会话元数据；
     * {@code runMeta} 写入 ASSISTANT 消息 agent_meta（仅后台观测）。无附件。
     */
    void recordExchange(
            AgentAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta);

    /**
     * 同上，并把 {@code userAttachments} 写入 USER 消息。null 或空表示本轮无图。ASSISTANT 不写附件。
     * 不写 citations。
     */
    void recordExchange(
            AgentAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments);

    /**
     * 同上。{@code citations} 非空时写入 ASSISTANT {@code agent_meta} 的 {@code citations} 字段，并保留已有 run meta。
     * null 或空列表不改变 {@code agent_meta}：空 run meta 仍为 null，非空 run meta 仍只写观测 JSON。
     * 不写本轮模型。
     */
    void recordExchange(
            AgentAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments,
            List<AgentCitation> citations);

    /**
     * 同上，并记录本轮归属：{@code requestedModelSpec}（请求的主用入口 spec）写入
     * {@code agent_meta.requestedModel}、{@code mode} 写入 {@code agent_meta.mode}。
     * 执行层回报的实际作答模型在 {@code runMeta.model}，**不得**被请求值覆盖
     * （备援切换后两者不同，正是事后排查的依据，Issue #145 F2）。null 或空白不写对应字段。
     */
    void recordExchange(
            AgentAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments,
            List<AgentCitation> citations,
            String requestedModelSpec,
            String mode);

    List<AgentAiConversationDto> listConversations(long agentId, long agentUserId);

    List<AgentAiMessageDto> listMessages(long agentId, long agentUserId, long conversationId);

    /** 按 (agentId, agentUserId) 作用域重命名；title 由实现 trim 后写回。不存在或非本人为 NOT_FOUND。 */
    AgentAiConversationDto renameConversation(long agentId, long agentUserId, long conversationId, String title);

    /**
     * 硬删本人会话。不存在或非本人为 NOT_FOUND，且不删除任何行。
     * {@code agent_ai_message.conversation_id} 为 ON DELETE RESTRICT，须先删消息再删会话。
     */
    void deleteConversation(long agentId, long agentUserId, long conversationId);

    /**
     * 硬删该会话中 {@code fromMessageId} 及其之后全部消息（供前端「编辑消息」与「重新生成」做真截断，
     * 避免被截断的旧轮次在刷新后复活、或进入后续历史）。会话或目标消息不存在/非本人为 NOT_FOUND；
     * 截断后刷新 {@code lastMessageAt} 为剩余最新消息时间（无剩余时回退会话创建时间），
     * 并清空 Todo 快照（它属于被截断的较新轮次，下一轮会重写）。
     */
    void truncateMessages(long agentId, long agentUserId, long conversationId, long fromMessageId);

    /**
     * 创建或刷新该会话的分享：已有分享时复用 token 并把快照更新到当前内容，没有时新建（随机 token）。
     * 会话不存在/非本人为 NOT_FOUND。
     */
    AgentAiShareDto createOrRefreshShare(long agentId, long agentUserId, long conversationId);

    /** 该会话当前的分享，没有时返回 null。不存在/非本人为 NOT_FOUND。 */
    AgentAiShareDto getShare(long agentId, long agentUserId, long conversationId);

    /** 撤销该会话的分享（物理删行，token 立即失效）。不存在/非本人为 NOT_FOUND。 */
    void revokeShare(long agentId, long agentUserId, long conversationId);

    /** 公开读：凭 token 读分享快照。token 空白、不存在或快照损坏一律 NOT_FOUND，不泄露原因。 */
    AgentAiSharedConversationDto getSharedConversation(String token);

    /**
     * 跨会话全文搜索（站点＋本人作用域）：消息正文 ILIKE 匹配（通配符已转义），
     * 按消息 id 倒序最多 30 条，每条带围绕命中词的摘要。空白查询返回空列表。
     */
    List<AgentAiSearchHitDto> searchConversations(long agentId, long agentUserId, String query);

    record ChatPreparation(
            AgentAiConversationEntity conversation,
            List<AgentChatHistoryMessage> history,
            AgentPromptProfile promptProfile,
            List<AgentTodoItem> todos) {}
}
