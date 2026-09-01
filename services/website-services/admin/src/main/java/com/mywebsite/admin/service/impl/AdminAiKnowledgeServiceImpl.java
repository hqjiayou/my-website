package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiKnowledgeServiceImpl implements AdminAiKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiKnowledgeServiceImpl.class);

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE, AdminAiKnowledgeDocumentEntity.STATUS_DISABLED);

    private final AdminAiKnowledgeDocumentMapper knowledgeMapper;
    private final AdminSiteMapper adminSiteMapper;
    private final AgentClient agentClient;

    public AdminAiKnowledgeServiceImpl(
            AdminAiKnowledgeDocumentMapper knowledgeMapper,
            AdminSiteMapper adminSiteMapper,
            AgentClient agentClient) {
        this.knowledgeMapper = knowledgeMapper;
        this.adminSiteMapper = adminSiteMapper;
        this.agentClient = agentClient;
    }

    @Override
    public PageResult<AdminAiKnowledgeListItemDto> listDocuments(
            long siteId, AdminAiKnowledgeListQuery query) {
        requireSite(siteId);
        int pageNum = query != null && query.pageNum() != null ? query.pageNum() : DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity> wrapper =
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .orderByDesc(AdminAiKnowledgeDocumentEntity::getUpdatedAt)
                        .orderByDesc(AdminAiKnowledgeDocumentEntity::getId);
        if (query != null && StringUtils.hasText(query.status())) {
            requireStatus(query.status());
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getStatus, query.status());
        }

        Page<AdminAiKnowledgeDocumentEntity> page =
                knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(AdminAiKnowledgeServiceImpl::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminAiKnowledgeDetailDto getDocument(long siteId, long documentId) {
        requireSite(siteId);
        return toDetail(requireDocumentInSite(siteId, documentId));
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto createDocument(long siteId, AdminAiKnowledgeCreateRequest request) {
        requireSite(siteId);
        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setSiteId(siteId);
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        knowledgeMapper.insert(entity);

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(siteId, entity.getId());
        syncDerivedIndex(saved);
        return toDetail(saved);
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto updateDocument(
            long siteId, long documentId, AdminAiKnowledgeUpdateRequest request) {
        requireSite(siteId);
        requireStatus(request.status());
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(siteId, documentId);
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setStatus(request.status());
        entity.setVersion(request.version());
        int updated = knowledgeMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(siteId, documentId);
        syncDerivedIndex(saved);
        return toDetail(saved);
    }

    @Override
    @Transactional
    public void deleteDocument(long siteId, long documentId) {
        requireSite(siteId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(siteId, documentId);
        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        knowledgeMapper.updateById(entity);
        int deleted = knowledgeMapper.deleteById(entity.getId());
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        removeFromDerivedIndex(siteId, documentId);
    }

    /**
     * 派生索引 best-effort 同步：SoT 在 Java，索引允许滞后；
     * agent-services 未配置检索（BLOCKED）或临时不可用时不阻断 KB CRUD，只记录日志。
     */
    private void syncDerivedIndex(AdminAiKnowledgeDocumentEntity entity) {
        try {
            if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
                agentClient.upsertRetrievalDocument(new AgentRetrievalDocument(
                        String.valueOf(entity.getSiteId()),
                        sourceId(entity.getId()),
                        entity.getTitle(),
                        entity.getContent()));
            } else {
                agentClient.deleteRetrievalDocument(
                        String.valueOf(entity.getSiteId()), sourceId(entity.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "KB derived index sync failed (siteId={}, documentId={}); index may lag",
                    entity.getSiteId(),
                    entity.getId(),
                    ex);
        }
    }

    private void removeFromDerivedIndex(long siteId, long documentId) {
        try {
            agentClient.deleteRetrievalDocument(String.valueOf(siteId), sourceId(documentId));
        } catch (RuntimeException ex) {
            log.warn(
                    "KB derived index delete failed (siteId={}, documentId={}); index may lag",
                    siteId,
                    documentId,
                    ex);
        }
    }

    private static String sourceId(long documentId) {
        return "kb:" + documentId;
    }

    private void requireSite(long siteId) {
        if (adminSiteMapper.selectById(siteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
    }

    private AdminAiKnowledgeDocumentEntity requireDocumentInSite(long siteId, long documentId) {
        AdminAiKnowledgeDocumentEntity document = knowledgeMapper.selectById(documentId);
        // 跨站或不存在统一 404，避免泄露归属
        if (document == null || !Long.valueOf(siteId).equals(document.getSiteId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return document;
    }

    private static void requireStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private static AdminAiKnowledgeListItemDto toListItem(AdminAiKnowledgeDocumentEntity entity) {
        return new AdminAiKnowledgeListItemDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static AdminAiKnowledgeDetailDto toDetail(AdminAiKnowledgeDocumentEntity entity) {
        return new AdminAiKnowledgeDetailDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
