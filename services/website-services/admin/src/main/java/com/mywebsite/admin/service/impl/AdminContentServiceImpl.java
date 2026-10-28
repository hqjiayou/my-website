package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminContentConstants;
import com.mywebsite.admin.constant.AdminContentMessageKeys;
import com.mywebsite.admin.constant.AdminMediaConstants;
import com.mywebsite.admin.constant.AdminMediaMessageKeys;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentCreateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentDetailDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListItemDto;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentListQuery;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminContentDtos.AdminContentUpdateRequest;
import com.mywebsite.admin.entity.AdminContentEntity;
import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminContentMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminContentServiceImpl implements AdminContentService {

    private final AdminContentMapper adminContentMapper;
    private final AdminAgentMapper adminAgentMapper;
    private final AdminMediaMapper adminMediaMapper;

    public AdminContentServiceImpl(
            AdminContentMapper adminContentMapper,
            AdminAgentMapper adminAgentMapper,
            AdminMediaMapper adminMediaMapper) {
        this.adminContentMapper = adminContentMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.adminMediaMapper = adminMediaMapper;
    }

    @Override
    public PageResult<AdminContentListItemDto> listContents(long agentId, AdminContentListQuery query) {
        requireSite(agentId);
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminContentConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminContentConstants.MAX_PAGE_SIZE)
                : AdminContentConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminContentEntity> wrapper = new LambdaQueryWrapper<AdminContentEntity>()
                .eq(AdminContentEntity::getAgentId, agentId)
                .orderByAsc(AdminContentEntity::getSortOrder)
                .orderByDesc(AdminContentEntity::getId);
        if (query != null) {
            if (StringUtils.hasText(query.type())) {
                requireType(query.type());
                wrapper.eq(AdminContentEntity::getType, query.type());
            }
            if (StringUtils.hasText(query.status())) {
                requireStatus(query.status());
                wrapper.eq(AdminContentEntity::getStatus, query.status());
            }
        }

        Page<AdminContentEntity> page =
                adminContentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminContentDetailDto getContent(long agentId, long contentId) {
        requireSite(agentId);
        return toDetail(requireContentInSite(agentId, contentId));
    }

    @Override
    @Transactional
    public AdminContentDetailDto createContent(long agentId, AdminContentCreateRequest request) {
        requireSite(agentId);
        requireType(request.type());
        requireStatus(request.status());
        if (request.coverMediaId() != null) {
            requireReadyCoverMediaInSite(agentId, request.coverMediaId());
        }

        AdminContentEntity entity = new AdminContentEntity();
        entity.setAgentId(agentId);
        entity.setType(request.type());
        entity.setTitle(request.title().trim());
        entity.setBody(blankToNull(request.body()));
        entity.setStatus(request.status());
        entity.setSortOrder(
                request.sortOrder() != null ? request.sortOrder() : AdminContentConstants.DEFAULT_SORT_ORDER);
        entity.setCoverMediaId(request.coverMediaId());
        applyPublishedAt(entity, request.status(), null);
        adminContentMapper.insert(entity);
        return toDetail(requireContentInSite(agentId, entity.getId()));
    }

    @Override
    @Transactional
    public AdminContentDetailDto updateContent(
            long agentId, long contentId, AdminContentUpdateRequest request) {
        requireSite(agentId);
        requireType(request.type());
        AdminContentEntity entity = requireContentInSite(agentId, contentId);

        if (request.coverMediaId() != null) {
            requireReadyCoverMediaInSite(agentId, request.coverMediaId());
        }

        entity.setType(request.type());
        entity.setTitle(request.title().trim());
        entity.setBody(blankToNull(request.body()));
        entity.setSortOrder(
                request.sortOrder() != null ? request.sortOrder() : AdminContentConstants.DEFAULT_SORT_ORDER);
        entity.setCoverMediaId(request.coverMediaId());
        entity.setVersion(request.version());
        int updated = adminContentMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminContentMessageKeys.CONTENT_VERSION_CONFLICT);
        }
        return toDetail(requireContentInSite(agentId, contentId));
    }

    @Override
    @Transactional
    public AdminContentDetailDto updateStatus(
            long agentId, long contentId, AdminContentStatusUpdateRequest request) {
        requireSite(agentId);
        requireStatus(request.status());
        AdminContentEntity entity = requireContentInSite(agentId, contentId);
        String previousStatus = entity.getStatus();
        entity.setStatus(request.status());
        applyPublishedAt(entity, request.status(), previousStatus);
        entity.setVersion(request.version());
        int updated = adminContentMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminContentMessageKeys.CONTENT_VERSION_CONFLICT);
        }
        return toDetail(requireContentInSite(agentId, contentId));
    }

    @Override
    @Transactional
    public void deleteContent(long agentId, long contentId) {
        requireSite(agentId);
        AdminContentEntity entity = requireContentInSite(agentId, contentId);
        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminContentMapper.updateById(entity);
        int deleted = adminContentMapper.deleteById(entity.getId());
        if (deleted == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminContentMessageKeys.CONTENT_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        List<AdminContentEntity> contents = adminContentMapper.selectList(
                new LambdaQueryWrapper<AdminContentEntity>().eq(AdminContentEntity::getAgentId, agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminContentEntity content : contents) {
            content.setDeletedAt(now);
            adminContentMapper.updateById(content);
            adminContentMapper.deleteById(content.getId());
        }
    }

    private AdminAgentEntity requireSite(long agentId) {
        AdminAgentEntity agent = adminAgentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private AdminContentEntity requireContentInSite(long agentId, long contentId) {
        AdminContentEntity content = adminContentMapper.selectById(contentId);
        // 跨站或不存在统一 404，避免泄露归属
        if (content == null || !Long.valueOf(agentId).equals(content.getAgentId())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminContentMessageKeys.CONTENT_NOT_FOUND);
        }
        return content;
    }

    private void requireReadyCoverMediaInSite(long agentId, long mediaId) {
        AdminMediaEntity media = adminMediaMapper.selectById(mediaId);
        if (media == null || !Long.valueOf(agentId).equals(media.getAgentId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminContentMessageKeys.CONTENT_COVER_MEDIA_INVALID);
        }
        if (!AdminMediaConstants.STATUS_READY.equals(media.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminMediaMessageKeys.MEDIA_NOT_READY);
        }
    }

    private static void requireType(String type) {
        if (!AdminContentConstants.ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminContentMessageKeys.CONTENT_TYPE_INVALID);
        }
    }

    private static void requireStatus(String status) {
        if (!AdminContentConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminContentMessageKeys.CONTENT_STATUS_INVALID);
        }
    }

    /**
     * 进入 PUBLISHED 时写入/刷新 published_at；从 PUBLISHED 离开时保留历史发布时间。
     */
    private static void applyPublishedAt(
            AdminContentEntity entity, String newStatus, String previousStatus) {
        if (AdminContentConstants.STATUS_PUBLISHED.equals(newStatus)) {
            if (!AdminContentConstants.STATUS_PUBLISHED.equals(previousStatus)
                    || entity.getPublishedAt() == null) {
                entity.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private AdminContentListItemDto toListItem(AdminContentEntity entity) {
        return new AdminContentListItemDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getCoverMediaId(),
                entity.getPublishedAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminContentDetailDto toDetail(AdminContentEntity entity) {
        return new AdminContentDetailDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getCoverMediaId(),
                entity.getPublishedAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
