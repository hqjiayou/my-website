package com.mywebsite.site.service;

import com.mywebsite.agent.dto.AgentChatHistoryMessage;
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
     * {@code runMeta} 写入 ASSISTANT 消息 agent_meta（仅后台观测）。
     */
    void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta);

    List<SiteAiConversationDto> listConversations(long siteId, long siteUserId);

    List<SiteAiMessageDto> listMessages(long siteId, long siteUserId, long conversationId);

    record ChatPreparation(
            SiteAiConversationEntity conversation,
            List<AgentChatHistoryMessage> history,
            AgentPromptProfile promptProfile,
            List<AgentTodoItem> todos) {}
}
