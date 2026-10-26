package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeFailureDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeFileDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeOverviewDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeSiteStatDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeDetailDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListItemDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeListQuery;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedConflictDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedFailureDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReembedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentDimensionChangeRequiredException;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.mywebsite.infra.properties.StorageProperties;
import com.mywebsite.infra.service.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final String REEMBED_SUCCESS = "SUCCESS";
    private static final String REEMBED_PARTIAL = "PARTIAL";

    private final AdminAiKnowledgeDocumentMapper knowledgeMapper;
    private final AdminSiteMapper adminSiteMapper;
    private final AgentClient agentClient;
    private final AdminAiModelEntryService modelEntryService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;

    public AdminAiKnowledgeServiceImpl(
            AdminAiKnowledgeDocumentMapper knowledgeMapper,
            AdminSiteMapper adminSiteMapper,
            AgentClient agentClient,
            AdminAiModelEntryService modelEntryService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties) {
        this.knowledgeMapper = knowledgeMapper;
        this.adminSiteMapper = adminSiteMapper;
        this.agentClient = agentClient;
        this.modelEntryService = modelEntryService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
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
    public AdminAiKnowledgeDetailDto createUploadedDocument(
            long siteId, AdminAiKnowledgeCreateRequest request, AdminAiKnowledgeUploadMeta meta) {
        requireSite(siteId);
        AdminAiKnowledgeDocumentEntity duplicate = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .eq(AdminAiKnowledgeDocumentEntity::getSha256, meta.sha256()));
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.FILE_DUPLICATE);
        }

        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setSiteId(siteId);
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        entity.setSourceFormat(meta.sourceFormat());
        entity.setOriginalFilename(meta.originalFilename());
        entity.setFileSizeBytes(meta.fileSizeBytes());
        entity.setSha256(meta.sha256());

        // 存储已配置时先落原档（key 按内容指纹，天然幂等）；落档失败诚实拒绝整单，不假装存了。
        String bucket = storageProperties == null ? null : storageProperties.getBucket();
        if (StringUtils.hasText(bucket)) {
            String objectKey =
                    "sites/" + siteId + "/knowledge/" + meta.sha256() + "." + meta.sourceFormat();
            try {
                objectStorageService.put(
                        bucket,
                        objectKey,
                        new ByteArrayInputStream(meta.bytes()),
                        meta.bytes().length,
                        meta.contentType());
            } catch (RuntimeException ex) {
                log.warn("KB original file put failed (siteId={}, key={})", siteId, objectKey, ex);
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR.getCode(), AdminAiKnowledgeMessageKeys.FILE_STORAGE_FAILED);
            }
            entity.setStorageBucket(bucket);
            entity.setStorageKey(objectKey);
        }
        knowledgeMapper.insert(entity);

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(siteId, entity.getId());
        syncDerivedIndex(saved);
        return toDetail(saved);
    }

    @Override
    public AdminAiKnowledgeOverviewDto knowledgeOverview() {
        List<AdminAiKnowledgeDocumentEntity> documents =
                knowledgeMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> siteNames = new LinkedHashMap<>();
        for (AdminSiteEntity site : adminSiteMapper.selectList(new LambdaQueryWrapper<>())) {
            siteNames.put(site.getId(), site.getSiteName());
        }
        long indexed = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(doc.getIndexStatus()))
                .count();
        long failed = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(doc.getIndexStatus()))
                .count();
        long originalBytes = documents.stream()
                .mapToLong(doc -> doc.getFileSizeBytes() == null ? 0L : doc.getFileSizeBytes())
                .sum();

        Map<Long, List<AdminAiKnowledgeDocumentEntity>> bySite = new LinkedHashMap<>();
        for (AdminAiKnowledgeDocumentEntity doc : documents) {
            bySite.computeIfAbsent(doc.getSiteId(), key -> new ArrayList<>()).add(doc);
        }
        List<AdminAiKnowledgeSiteStatDto> siteStats = new ArrayList<>();
        for (Map.Entry<Long, List<AdminAiKnowledgeDocumentEntity>> entry : bySite.entrySet()) {
            List<AdminAiKnowledgeDocumentEntity> siteDocs = entry.getValue();
            siteStats.add(new AdminAiKnowledgeSiteStatDto(
                    entry.getKey(),
                    siteNames.get(entry.getKey()),
                    siteDocs.size(),
                    siteDocs.stream()
                            .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(
                                    doc.getIndexStatus()))
                            .count(),
                    siteDocs.stream()
                            .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(
                                    doc.getIndexStatus()))
                            .count(),
                    siteDocs.stream()
                            .mapToLong(doc -> doc.getFileSizeBytes() == null ? 0L : doc.getFileSizeBytes())
                            .sum()));
        }
        siteStats.sort((a, b) -> a.siteId().compareTo(b.siteId()));

        List<AdminAiKnowledgeFailureDto> recentFailures = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(doc.getIndexStatus()))
                .sorted((a, b) -> {
                    OffsetDateTime left = a.getUpdatedAt() == null ? OffsetDateTime.MIN : a.getUpdatedAt();
                    OffsetDateTime right = b.getUpdatedAt() == null ? OffsetDateTime.MIN : b.getUpdatedAt();
                    return right.compareTo(left);
                })
                .limit(10)
                .map(doc -> new AdminAiKnowledgeFailureDto(
                        doc.getId(),
                        doc.getSiteId(),
                        siteNames.get(doc.getSiteId()),
                        doc.getTitle(),
                        doc.getIndexError(),
                        doc.getUpdatedAt()))
                .toList();

        return new AdminAiKnowledgeOverviewDto(
                documents.size(),
                indexed,
                failed,
                documents.size() - indexed - failed,
                originalBytes,
                siteStats,
                recentFailures);
    }

    @Override
    public AdminAiKnowledgeReindexFailedResultDto reindexFailedDocuments() {
        List<AdminAiKnowledgeDocumentEntity> failedDocuments = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(
                                AdminAiKnowledgeDocumentEntity::getIndexStatus,
                                AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED)
                        .eq(AdminAiKnowledgeDocumentEntity::getStatus, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE));
        int succeeded = 0;
        for (AdminAiKnowledgeDocumentEntity document : failedDocuments) {
            syncDerivedIndex(document);
            AdminAiKnowledgeDocumentEntity refreshed = knowledgeMapper.selectById(document.getId());
            if (refreshed != null
                    && AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(refreshed.getIndexStatus())) {
                succeeded++;
            }
        }
        return new AdminAiKnowledgeReindexFailedResultDto(failedDocuments.size(), succeeded);
    }

    @Override
    public AdminAiKnowledgeFileDto originalFileUrl(long siteId, long documentId) {
        requireSite(siteId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(siteId, documentId);
        if (!StringUtils.hasText(entity.getStorageKey())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.FILE_ORIGINAL_MISSING);
        }
        String url = objectStorageService.presignedGet(
                entity.getStorageBucket(), entity.getStorageKey(), Duration.ofMinutes(10));
        return new AdminAiKnowledgeFileDto(url, entity.getOriginalFilename(), entity.getSourceFormat());
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
    public AdminAiKnowledgeDetailDto reindexDocument(long siteId, long documentId) {
        requireSite(siteId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(siteId, documentId);
        if (!AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        syncDerivedIndex(entity);
        return toDetail(entity);
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
        removeOriginalObject(entity);
    }

    /** 原档清理（best-effort）：同站点同指纹只可能有一篇（上传去重），删文档即可删对象。 */
    private void removeOriginalObject(AdminAiKnowledgeDocumentEntity entity) {
        if (!StringUtils.hasText(entity.getStorageKey())) {
            return;
        }
        try {
            objectStorageService.remove(entity.getStorageBucket(), entity.getStorageKey());
        } catch (RuntimeException ex) {
            log.warn(
                    "KB original file remove failed (siteId={}, documentId={}); object may linger",
                    entity.getSiteId(),
                    entity.getId(),
                    ex);
        }
    }

    @Override
    @Transactional
    public void softDeleteBySiteId(long siteId) {
        List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getSiteId, siteId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminAiKnowledgeDocumentEntity document : documents) {
            document.setDeletedAt(now);
            knowledgeMapper.updateById(document);
            knowledgeMapper.deleteById(document.getId());
            removeFromDerivedIndex(siteId, document.getId());
        }
    }

    @Override
    public AdminAiKnowledgeReembedResultDto reembedAll() {
        List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getSiteId)
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getId));
        Map<Long, List<AdminAiKnowledgeDocumentEntity>> documentsBySite = new LinkedHashMap<>();
        for (AdminAiKnowledgeDocumentEntity document : documents) {
            if (document.getSiteId() == null || document.getId() == null) {
                continue;
            }
            documentsBySite.computeIfAbsent(document.getSiteId(), ignored -> new ArrayList<>()).add(document);
        }
        if (documentsBySite.isEmpty()) {
            return new AdminAiKnowledgeReembedResultDto(0, 0, null, false, List.of(), REEMBED_SUCCESS);
        }

        Map<SelectionGroup, List<Long>> sitesBySelection = new LinkedHashMap<>();
        Map<Long, AgentModelSelection> selectionBySite = new LinkedHashMap<>();
        for (Long siteId : documentsBySite.keySet()) {
            AgentModelSelection selection = modelEntryService.currentSelection(siteId);
            selectionBySite.put(siteId, selection);
            sitesBySelection.computeIfAbsent(new SelectionGroup(selection), ignored -> new ArrayList<>()).add(siteId);
        }

        Map<Long, Integer> dimensionsBySite = new LinkedHashMap<>();
        Integer columnDimensions = null;
        AgentModelSelection confirmSelection = null;
        boolean sawSelection = false;
        for (Map.Entry<SelectionGroup, List<Long>> group : sitesBySelection.entrySet()) {
            Probe probe = probeSelection(group.getKey().selection());
            if (!sawSelection) {
                confirmSelection = group.getKey().selection();
                sawSelection = true;
            }
            if (probe.columnDimensions() != null) {
                columnDimensions = probe.columnDimensions();
            }
            for (Long siteId : group.getValue()) {
                dimensionsBySite.put(siteId, probe.dimensions());
            }
        }

        if (new LinkedHashSet<>(dimensionsBySite.values()).size() > 1) {
            List<AdminAiKnowledgeReembedConflictDto> conflicts = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : dimensionsBySite.entrySet()) {
                conflicts.add(new AdminAiKnowledgeReembedConflictDto(entry.getKey(), entry.getValue()));
            }
            throw new ReembedConflictException(conflicts);
        }

        int dimensions = dimensionsBySite.values().iterator().next();
        boolean recreatedSchema = false;
        if (columnDimensions == null || columnDimensions != dimensions) {
            AgentReembedPrepareResult recreated = agentClient.prepareReembed(confirmSelection, true);
            dimensions = recreated.dimensions();
            recreatedSchema = recreated.recreated();
        }

        List<AdminAiKnowledgeReembedFailureDto> failed = new ArrayList<>();
        int attempted = 0;
        for (Map.Entry<Long, List<AdminAiKnowledgeDocumentEntity>> site : documentsBySite.entrySet()) {
            AgentModelSelection selection = selectionBySite.get(site.getKey());
            for (AdminAiKnowledgeDocumentEntity document : site.getValue()) {
                if (!AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(document.getStatus())) {
                    continue;
                }
                attempted++;
                try {
                    upsertDerivedIndex(document, selection);
                    recordIndexOutcome(document, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null);
                } catch (RuntimeException ex) {
                    recordIndexOutcome(document, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ex));
                    failed.add(new AdminAiKnowledgeReembedFailureDto(
                            document.getSiteId(), sourceId(document.getId()), indexError(ex)));
                }
            }
        }
        return new AdminAiKnowledgeReembedResultDto(
                documentsBySite.size(),
                attempted,
                dimensions,
                recreatedSchema,
                List.copyOf(failed),
                failed.isEmpty() ? REEMBED_SUCCESS : REEMBED_PARTIAL);
    }

    private Probe probeSelection(AgentModelSelection selection) {
        try {
            AgentReembedPrepareResult result = agentClient.prepareReembed(selection, false);
            return new Probe(result.dimensions(), result.columnDimensions());
        } catch (AgentDimensionChangeRequiredException ex) {
            return new Probe(ex.dimensions(), ex.columnDimensions());
        }
    }

    /**
     * 派生索引 best-effort 同步：SoT 在 Java，索引允许滞后；
     * agent-services 未配置检索（BLOCKED）或临时不可用时不阻断 KB CRUD，只记录日志。
     */
    private void syncDerivedIndex(AdminAiKnowledgeDocumentEntity entity) {
        try {
            if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
                upsertDerivedIndex(entity, modelEntryService.currentSelection(entity.getSiteId()));
                recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null);
            } else {
                agentClient.deleteRetrievalDocument(
                        String.valueOf(entity.getSiteId()), sourceId(entity.getId()));
                recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING, null);
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "KB derived index sync failed (siteId={}, documentId={}); index may lag",
                    entity.getSiteId(),
                    entity.getId(),
                    ex);
            if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
                recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ex));
            }
        }
    }

    /**
     * 落库索引结果（定向更新三列，不动乐观锁 version）：INDEXED 时写 indexedAt 并清 error；
     * FAILED 时保留上次 indexedAt 只记 error；PENDING（未在索引中）清两者。
     * 同步更新内存实体，让本次请求的返回 DTO 立刻反映新状态。
     */
    private void recordIndexOutcome(
            AdminAiKnowledgeDocumentEntity entity, String indexStatus, String indexError) {
        OffsetDateTime indexedAt =
                AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(indexStatus)
                        ? OffsetDateTime.now(ZoneOffset.UTC)
                        : AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING.equals(indexStatus)
                                ? null
                                : entity.getIndexedAt();
        String error =
                indexError == null ? null : indexError.substring(0, Math.min(indexError.length(), 500));
        knowledgeMapper.update(
                null,
                new LambdaUpdateWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getId, entity.getId())
                        .set(AdminAiKnowledgeDocumentEntity::getIndexStatus, indexStatus)
                        .set(AdminAiKnowledgeDocumentEntity::getIndexError, error)
                        .set(AdminAiKnowledgeDocumentEntity::getIndexedAt, indexedAt));
        entity.setIndexStatus(indexStatus);
        entity.setIndexError(error);
        entity.setIndexedAt(indexedAt);
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

    private void upsertDerivedIndex(AdminAiKnowledgeDocumentEntity entity, AgentModelSelection selection) {
        agentClient.upsertRetrievalDocument(new AgentRetrievalDocument(
                String.valueOf(entity.getSiteId()),
                sourceId(entity.getId()),
                entity.getTitle(),
                entity.getContent(),
                selection == null ? null : selection.embeddingSpec(),
                selection == null ? null : selection.apiKey(),
                selection == null ? null : selection.baseUrl()));
    }

    private static String indexError(RuntimeException ex) {
        if (ex instanceof BusinessException business) {
            return business.getMessageKey();
        }
        return ex.getClass().getSimpleName();
    }

    private static String sourceId(long documentId) {
        return "kb:" + documentId;
    }

    private record SelectionGroup(AgentModelSelection selection) {}

    private record Probe(int dimensions, Integer columnDimensions) {}

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
                entity.getIndexStatus(),
                entity.getIndexError(),
                entity.getIndexedAt(),
                entity.getSourceFormat(),
                entity.getOriginalFilename(),
                entity.getFileSizeBytes(),
                StringUtils.hasText(entity.getStorageKey()),
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
                entity.getIndexStatus(),
                entity.getIndexError(),
                entity.getIndexedAt(),
                entity.getSourceFormat(),
                entity.getOriginalFilename(),
                entity.getFileSizeBytes(),
                StringUtils.hasText(entity.getStorageKey()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
