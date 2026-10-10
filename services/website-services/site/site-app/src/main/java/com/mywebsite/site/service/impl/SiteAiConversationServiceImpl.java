package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SiteAiConversationDto;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.entity.SiteAiConversationEntity;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import com.mywebsite.site.entity.SiteAiPromptProfileEntity;
import com.mywebsite.site.mapper.SiteAiConversationMapper;
import com.mywebsite.site.mapper.SiteAiMessageMapper;
import com.mywebsite.site.mapper.SiteAiPromptProfileMapper;
import com.mywebsite.site.service.SiteAiConversationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SiteAiConversationServiceImpl implements SiteAiConversationService {

    /** 每次 chat 携带的最大历史条数（含 USER/ASSISTANT），控制上下文成本。 */
    private static final int HISTORY_LIMIT = 20;

    private static final int TITLE_MAX_LENGTH = 60;
    private static final int CONVERSATION_LIST_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(SiteAiConversationServiceImpl.class);

    private final SiteAiConversationMapper conversationMapper;
    private final SiteAiMessageMapper messageMapper;
    private final SiteAiPromptProfileMapper promptProfileMapper;
    private final ObjectMapper objectMapper;

    public SiteAiConversationServiceImpl(
            SiteAiConversationMapper conversationMapper,
            SiteAiMessageMapper messageMapper,
            SiteAiPromptProfileMapper promptProfileMapper,
            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.promptProfileMapper = promptProfileMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatPreparation prepare(long siteId, long siteUserId, String conversationIdOrNull) {
        SiteAiConversationEntity conversation;
        List<AgentChatHistoryMessage> history;
        if (StringUtils.hasText(conversationIdOrNull)) {
            conversation = requireOwnConversation(siteId, siteUserId, parseId(conversationIdOrNull));
            history = loadHistory(conversation.getId());
        } else {
            conversation = new SiteAiConversationEntity();
            conversation.setSiteId(siteId);
            conversation.setSiteUserId(siteUserId);
            history = List.of();
        }
        return new ChatPreparation(conversation, history, loadPromptProfile(siteId), readTodos(conversation));
    }

    @Override
    @Transactional
    public void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        conversation.setAgentTodos(writeTodos(todos));
        if (conversation.getId() == null) {
            conversation.setTitle(truncateTitle(userMessage));
            conversation.setLastMessageAt(now);
            conversationMapper.insert(conversation);
        } else {
            conversation.setLastMessageAt(now);
            conversationMapper.updateById(conversation);
        }
        insertMessage(conversation, SiteAiMessageEntity.ROLE_USER, userMessage, null);
        insertMessage(conversation, SiteAiMessageEntity.ROLE_ASSISTANT, reply, writeRunMeta(runMeta));
    }

    @Override
    public List<SiteAiConversationDto> listConversations(long siteId, long siteUserId) {
        List<SiteAiConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<SiteAiConversationEntity>()
                        .eq(SiteAiConversationEntity::getSiteId, siteId)
                        .eq(SiteAiConversationEntity::getSiteUserId, siteUserId)
                        .orderByDesc(SiteAiConversationEntity::getLastMessageAt)
                        .last("LIMIT " + CONVERSATION_LIST_LIMIT));
        return conversations.stream()
                .map(c -> new SiteAiConversationDto(
                        c.getId(), c.getTitle(), c.getLastMessageAt(), c.getCreatedAt()))
                .toList();
    }

    @Override
    public List<SiteAiMessageDto> listMessages(long siteId, long siteUserId, long conversationId) {
        requireOwnConversation(siteId, siteUserId, conversationId);
        return messageMapper
                .selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversationId)
                        .orderByAsc(SiteAiMessageEntity::getId))
                .stream()
                .map(m -> new SiteAiMessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    private SiteAiConversationEntity requireOwnConversation(long siteId, long siteUserId, long conversationId) {
        SiteAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        // 跨站/跨用户统一 404，不泄露归属
        if (conversation == null
                || !Objects.equals(conversation.getSiteId(), siteId)
                || !Objects.equals(conversation.getSiteUserId(), siteUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    private List<AgentChatHistoryMessage> loadHistory(long conversationId) {
        List<SiteAiMessageEntity> latest = messageMapper.selectList(
                new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(SiteAiMessageEntity::getId)
                        .last("LIMIT " + HISTORY_LIMIT));
        return latest.stream()
                .sorted(Comparator.comparing(SiteAiMessageEntity::getId))
                .map(m -> new AgentChatHistoryMessage(m.getRole(), m.getContent()))
                .toList();
    }

    private AgentPromptProfile loadPromptProfile(long siteId) {
        SiteAiPromptProfileEntity profile = promptProfileMapper.selectOne(
                new LambdaQueryWrapper<SiteAiPromptProfileEntity>()
                        .eq(SiteAiPromptProfileEntity::getSiteId, siteId)
                        .last("LIMIT 1"));
        if (profile == null) {
            return null;
        }
        Double temperature = profile.getTemperature() == null ? null : profile.getTemperature().doubleValue();
        return new AgentPromptProfile(profile.getSystemPrompt(), temperature);
    }

    private List<AgentTodoItem> readTodos(SiteAiConversationEntity conversation) {
        String raw = conversation.getAgentTodos();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(raw, AgentTodoItem[].class));
        } catch (JsonProcessingException ex) {
            // 脏快照不阻断聊天：按无 todo 继续，下一轮成功后被覆盖
            log.warn("site ai conversation {} has malformed agent_todos, ignored", conversation.getId(), ex);
            return List.of();
        }
    }

    private String writeTodos(List<AgentTodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(todos);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent todos", ex);
        }
    }

    /** 观测数据序列化失败不得让已成功的一轮回滚：记日志后存 NULL。 */
    private String writeRunMeta(AgentRunMeta runMeta) {
        if (runMeta == null || runMeta.equals(AgentRunMeta.EMPTY)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(runMeta);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize agent run meta, stored as null", ex);
            return null;
        }
    }

    private void insertMessage(
            SiteAiConversationEntity conversation, String role, String content, String agentMeta) {
        SiteAiMessageEntity message = new SiteAiMessageEntity();
        message.setConversationId(conversation.getId());
        message.setSiteId(conversation.getSiteId());
        message.setRole(role);
        message.setContent(content);
        message.setAgentMeta(agentMeta);
        messageMapper.insert(message);
    }

    private static long parseId(String conversationId) {
        try {
            return Long.parseLong(conversationId.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private static String truncateTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }
}
