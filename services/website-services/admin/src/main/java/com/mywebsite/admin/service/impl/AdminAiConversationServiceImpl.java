package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListItemDto;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListQuery;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiMessageDto;
import com.mywebsite.admin.entity.AdminAiConversationEntity;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.mapper.AdminAiConversationMapper;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentRunMeta;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiConversationServiceImpl implements AdminAiConversationService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiConversationServiceImpl.class);

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 与 agent-app 会话标题上限一致（首条用户消息截断）。 */
    private static final int PREVIEW_TITLE_MAX_LENGTH = 60;

    private final AdminAiConversationMapper conversationMapper;
    private final AdminAiMessageMapper messageMapper;
    private final AdminAgentMapper adminAgentMapper;
    private final ObjectMapper objectMapper;

    public AdminAiConversationServiceImpl(
            AdminAiConversationMapper conversationMapper,
            AdminAiMessageMapper messageMapper,
            AdminAgentMapper adminAgentMapper,
            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AdminAiConversationListItemDto> listConversations(
            long agentId, AdminAiConversationListQuery query) {
        requireSite(agentId);
        int pageNum = query != null && query.pageNum() != null ? query.pageNum() : DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminAiConversationEntity> wrapper =
                new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getAgentId, agentId)
                        .orderByDesc(AdminAiConversationEntity::getLastMessageAt)
                        .orderByDesc(AdminAiConversationEntity::getId);
        Page<AdminAiConversationEntity> page =
                conversationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(AdminAiConversationServiceImpl::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public List<AdminAiMessageDto> listMessages(long agentId, long conversationId) {
        requireSite(agentId);
        AdminAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Long.valueOf(agentId).equals(conversation.getAgentId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return messageMapper
                .selectList(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, conversationId)
                        .eq(AdminAiMessageEntity::getAgentId, agentId)
                        .orderByAsc(AdminAiMessageEntity::getId))
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AdminAiMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AdminAiMessageEntity>().eq(AdminAiMessageEntity::getAgentId, agentId));
        for (AdminAiMessageEntity message : messages) {
            message.setDeletedAt(now);
            messageMapper.updateById(message);
            messageMapper.deleteById(message.getId());
        }
        List<AdminAiConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getAgentId, agentId));
        for (AdminAiConversationEntity conversation : conversations) {
            conversation.setDeletedAt(now);
            conversationMapper.updateById(conversation);
            conversationMapper.deleteById(conversation.getId());
        }
    }

    @Override
    public void requirePreviewConversation(long agentId, long conversationId) {
        loadPreviewConversation(agentId, conversationId);
    }

    @Override
    @Transactional
    public long recordPreviewExchange(
            long agentId, Long conversationId, String userMessage, String reply, AgentRunMeta runMeta) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdminAiConversationEntity conversation;
        if (conversationId == null) {
            conversation = new AdminAiConversationEntity();
            conversation.setAgentId(agentId);
            conversation.setSource(AdminAiConversationEntity.SOURCE_ADMIN_PREVIEW);
            conversation.setTitle(truncateTitle(userMessage));
            conversation.setLastMessageAt(now);
            conversationMapper.insert(conversation);
        } else {
            conversation = loadPreviewConversation(agentId, conversationId);
            conversation.setLastMessageAt(now);
            conversationMapper.updateById(conversation);
        }
        insertMessage(conversation, AdminAiMessageEntity.ROLE_USER, userMessage, null);
        insertMessage(conversation, AdminAiMessageEntity.ROLE_ASSISTANT, reply, writeRunMeta(runMeta));
        return conversation.getId();
    }

    private AdminAiConversationEntity loadPreviewConversation(long agentId, long conversationId) {
        AdminAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !Long.valueOf(agentId).equals(conversation.getAgentId())
                || !AdminAiConversationEntity.SOURCE_ADMIN_PREVIEW.equals(conversation.getSource())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    private void insertMessage(AdminAiConversationEntity conversation, String role, String content, String agentMeta) {
        AdminAiMessageEntity message = new AdminAiMessageEntity();
        message.setConversationId(conversation.getId());
        message.setAgentId(conversation.getAgentId());
        message.setRole(role);
        message.setContent(content);
        message.setAgentMeta(agentMeta);
        messageMapper.insert(message);
    }

    /** 观测数据序列化失败不得让已成功的一轮回滚：记日志后存 NULL（与 agent-app 同口径）。 */
    private String writeRunMeta(AgentRunMeta runMeta) {
        if (runMeta == null || runMeta.equals(AgentRunMeta.EMPTY)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(runMeta);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize preview agent run meta, stored as null", ex);
            return null;
        }
    }

    private static String truncateTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= PREVIEW_TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, PREVIEW_TITLE_MAX_LENGTH);
    }

    private void requireSite(long agentId) {
        if (adminAgentMapper.selectById(agentId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    private static AdminAiConversationListItemDto toListItem(AdminAiConversationEntity entity) {
        return new AdminAiConversationListItemDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getAgentUserId(),
                entity.getSource(),
                entity.getTitle(),
                entity.getLastMessageAt(),
                entity.getCreatedAt());
    }

    private AdminAiMessageDto toMessage(AdminAiMessageEntity entity) {
        return new AdminAiMessageDto(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt(),
                readAgentMeta(entity));
    }

    /** 观测数据损坏不影响会话浏览：忽略并记日志。 */
    private AgentRunMeta readAgentMeta(AdminAiMessageEntity entity) {
        if (!StringUtils.hasText(entity.getAgentMeta())) {
            return null;
        }
        try {
            return objectMapper
                    .readerFor(AgentRunMeta.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(entity.getAgentMeta());
        } catch (JsonProcessingException ex) {
            log.warn("agent_ai_message {} has malformed agent_meta, ignored", entity.getId(), ex);
            return null;
        }
    }
}
