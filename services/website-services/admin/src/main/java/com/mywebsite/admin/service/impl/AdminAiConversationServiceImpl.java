package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListItemDto;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiConversationListQuery;
import com.mywebsite.admin.dto.AdminAiConversationDtos.AdminAiMessageDto;
import com.mywebsite.admin.entity.AdminAiConversationEntity;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.mapper.AdminAiConversationMapper;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
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

    private final AdminAiConversationMapper conversationMapper;
    private final AdminAiMessageMapper messageMapper;
    private final AdminSiteMapper adminSiteMapper;
    private final ObjectMapper objectMapper;

    public AdminAiConversationServiceImpl(
            AdminAiConversationMapper conversationMapper,
            AdminAiMessageMapper messageMapper,
            AdminSiteMapper adminSiteMapper,
            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.adminSiteMapper = adminSiteMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AdminAiConversationListItemDto> listConversations(
            long siteId, AdminAiConversationListQuery query) {
        requireSite(siteId);
        int pageNum = query != null && query.pageNum() != null ? query.pageNum() : DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminAiConversationEntity> wrapper =
                new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getSiteId, siteId)
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
    public List<AdminAiMessageDto> listMessages(long siteId, long conversationId) {
        requireSite(siteId);
        AdminAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Long.valueOf(siteId).equals(conversation.getSiteId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return messageMapper
                .selectList(new LambdaQueryWrapper<AdminAiMessageEntity>()
                        .eq(AdminAiMessageEntity::getConversationId, conversationId)
                        .eq(AdminAiMessageEntity::getSiteId, siteId)
                        .orderByAsc(AdminAiMessageEntity::getId))
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void softDeleteBySiteId(long siteId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AdminAiMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AdminAiMessageEntity>().eq(AdminAiMessageEntity::getSiteId, siteId));
        for (AdminAiMessageEntity message : messages) {
            message.setDeletedAt(now);
            messageMapper.updateById(message);
            messageMapper.deleteById(message.getId());
        }
        List<AdminAiConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<AdminAiConversationEntity>()
                        .eq(AdminAiConversationEntity::getSiteId, siteId));
        for (AdminAiConversationEntity conversation : conversations) {
            conversation.setDeletedAt(now);
            conversationMapper.updateById(conversation);
            conversationMapper.deleteById(conversation.getId());
        }
    }

    private void requireSite(long siteId) {
        if (adminSiteMapper.selectById(siteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
    }

    private static AdminAiConversationListItemDto toListItem(AdminAiConversationEntity entity) {
        return new AdminAiConversationListItemDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getSiteUserId(),
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
            log.warn("site_ai_message {} has malformed agent_meta, ignored", entity.getId(), ex);
            return null;
        }
    }
}
