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
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminAiConversationServiceImpl implements AdminAiConversationService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAiConversationMapper conversationMapper;
    private final AdminAiMessageMapper messageMapper;
    private final AdminSiteMapper adminSiteMapper;

    public AdminAiConversationServiceImpl(
            AdminAiConversationMapper conversationMapper,
            AdminAiMessageMapper messageMapper,
            AdminSiteMapper adminSiteMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.adminSiteMapper = adminSiteMapper;
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
                .map(AdminAiConversationServiceImpl::toMessage)
                .toList();
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

    private static AdminAiMessageDto toMessage(AdminAiMessageEntity entity) {
        return new AdminAiMessageDto(
                entity.getId(), entity.getRole(), entity.getContent(), entity.getCreatedAt());
    }
}
