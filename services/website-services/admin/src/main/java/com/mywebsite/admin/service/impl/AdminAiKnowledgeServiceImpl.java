package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto;
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
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseLinkEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseLinkMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentDimensionChangeRequiredException;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentOcrImageRequest;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
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
    private final AdminAiKnowledgeBaseMapper baseMapper;
    private final AdminAiKnowledgeBaseLinkMapper linkMapper;

    public AdminAiKnowledgeServiceImpl(
            AdminAiKnowledgeDocumentMapper knowledgeMapper,
            AdminSiteMapper adminSiteMapper,
            AgentClient agentClient,
            AdminAiModelEntryService modelEntryService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties,
            AdminAiKnowledgeBaseMapper baseMapper,
            AdminAiKnowledgeBaseLinkMapper linkMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.adminSiteMapper = adminSiteMapper;
        this.agentClient = agentClient;
        this.modelEntryService = modelEntryService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.baseMapper = baseMapper;
        this.linkMapper = linkMapper;
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
                        .orderByDesc(AdminAiKnowledgeDocumentEntity::getUpdatedAt)
                        .orderByDesc(AdminAiKnowledgeDocumentEntity::getId);
        if (query != null && query.baseId() != null) {
            // 指定知识库：须对本站可见（自有或被共享链接），文档按 base 归属查（链接库的文档属于所有者站点）
            requireVisibleBase(siteId, query.baseId());
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getBaseId, query.baseId());
        } else {
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getSiteId, siteId);
        }
        if (query != null && StringUtils.hasText(query.status())) {
            requireStatus(query.status());
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getStatus, query.status());
        }

        Page<AdminAiKnowledgeDocumentEntity> page =
                knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(entity -> toListItem(entity, siteId)).toList(),
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
        entity.setBaseId(resolveWritableBaseId(siteId, request.baseId()));
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
        entity.setBaseId(resolveWritableBaseId(siteId, request.baseId()));
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
        AdminAiKnowledgeDocumentEntity entity = knowledgeMapper.selectById(documentId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Long.valueOf(siteId).equals(entity.getSiteId())) {
            // 非所有者：仅当文档所在知识库共享给本站时可见
            if (entity.getBaseId() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            requireVisibleBase(siteId, entity.getBaseId());
        }
        if (!StringUtils.hasText(entity.getStorageKey())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.FILE_ORIGINAL_MISSING);
        }
        String url = objectStorageService.presignedGet(
                entity.getStorageBucket(), entity.getStorageKey(), Duration.ofMinutes(10));
        return new AdminAiKnowledgeFileDto(url, entity.getOriginalFilename(), entity.getSourceFormat());
    }

    // ---------- 知识库（base）与共享链接 ----------

    @Override
    public List<AdminAiKnowledgeBaseDto> listBasesForSite(long siteId) {
        requireSite(siteId);
        List<AdminAiKnowledgeBaseEntity> visible = new ArrayList<>();
        visible.addAll(baseMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                .eq(AdminAiKnowledgeBaseEntity::getSiteId, siteId)
                .orderByAsc(AdminAiKnowledgeBaseEntity::getId)));
        List<AdminAiKnowledgeBaseLinkEntity> links = linkMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getSiteId, siteId));
        for (AdminAiKnowledgeBaseLinkEntity link : links) {
            AdminAiKnowledgeBaseEntity base = baseMapper.selectById(link.getBaseId());
            if (base != null) {
                visible.add(base);
            }
        }
        Map<Long, String> siteNames = siteNames();
        return visible.stream().map(base -> toBaseDto(base, siteId, siteNames)).toList();
    }

    @Override
    public List<AdminAiKnowledgeBaseDto> listAllBases() {
        Map<Long, String> siteNames = siteNames();
        return baseMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getSiteId)
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getId))
                .stream()
                .map(base -> toBaseDto(base, base.getSiteId(), siteNames))
                .toList();
    }

    @Override
    @Transactional
    public AdminAiKnowledgeBaseDto createBase(long siteId, AdminAiKnowledgeBaseCreateRequest request) {
        requireSite(siteId);
        AdminAiKnowledgeBaseEntity base = new AdminAiKnowledgeBaseEntity();
        base.setSiteId(siteId);
        base.setName(request.name().trim());
        base.setIsDefault(false);
        base.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(base);
        return toBaseDto(baseMapper.selectById(base.getId()), siteId, siteNames());
    }

    @Override
    @Transactional
    public void deleteBase(long siteId, long baseId) {
        requireSite(siteId);
        AdminAiKnowledgeBaseEntity base = requireOwnedBase(siteId, baseId);
        if (Boolean.TRUE.equals(base.getIsDefault())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.BASE_IS_DEFAULT);
        }
        long documents = knowledgeMapper.selectCount(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                .eq(AdminAiKnowledgeDocumentEntity::getBaseId, baseId));
        if (documents > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_EMPTY);
        }
        // 该库文档为零，链接站点的分区里也没有它的文档，直接清链接行即可
        linkMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId));
        baseMapper.deleteById(baseId);
    }

    @Override
    @Transactional
    public void linkBase(long baseId, long targetSiteId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        requireSite(targetSiteId);
        if (Long.valueOf(targetSiteId).equals(base.getSiteId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.BASE_LINK_SELF);
        }
        AdminAiKnowledgeBaseLinkEntity existing = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getSiteId, targetSiteId));
        if (existing != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.BASE_ALREADY_LINKED);
        }
        AdminAiKnowledgeBaseLinkEntity link = new AdminAiKnowledgeBaseLinkEntity();
        link.setBaseId(baseId);
        link.setSiteId(targetSiteId);
        linkMapper.insert(link);
        // 扇出：该库现存 ACTIVE 文档同步进目标站点分区（best-effort，失败只记日志）
        for (AdminAiKnowledgeDocumentEntity document : activeDocumentsOfBase(baseId)) {
            try {
                upsertDerivedIndexForSite(document, targetSiteId);
            } catch (RuntimeException ex) {
                log.warn(
                        "KB link fan-out upsert failed (baseId={}, targetSiteId={}, documentId={})",
                        baseId,
                        targetSiteId,
                        document.getId(),
                        ex);
            }
        }
    }

    @Override
    @Transactional
    public void unlinkBase(long baseId, long targetSiteId) {
        AdminAiKnowledgeBaseLinkEntity existing = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getSiteId, targetSiteId));
        if (existing == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        linkMapper.deleteById(existing.getId());
        for (AdminAiKnowledgeDocumentEntity document : activeDocumentsOfBase(baseId)) {
            try {
                agentClient.deleteRetrievalDocument(String.valueOf(targetSiteId), sourceId(document.getId()));
            } catch (RuntimeException ex) {
                log.warn(
                        "KB unlink fan-out delete failed (baseId={}, targetSiteId={}, documentId={})",
                        baseId,
                        targetSiteId,
                        document.getId(),
                        ex);
            }
        }
    }

    private List<AdminAiKnowledgeDocumentEntity> activeDocumentsOfBase(long baseId) {
        return knowledgeMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                .eq(AdminAiKnowledgeDocumentEntity::getBaseId, baseId)
                .eq(AdminAiKnowledgeDocumentEntity::getStatus, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE));
    }

    private Map<Long, String> siteNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (AdminSiteEntity site : adminSiteMapper.selectList(new LambdaQueryWrapper<>())) {
            names.put(site.getId(), site.getSiteName());
        }
        return names;
    }

    private AdminAiKnowledgeBaseDto toBaseDto(
            AdminAiKnowledgeBaseEntity base, long viewerSiteId, Map<Long, String> siteNames) {
        long count = knowledgeMapper.selectCount(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                .eq(AdminAiKnowledgeDocumentEntity::getBaseId, base.getId()));
        List<Long> linkedSiteIds = linkMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, base.getId()))
                .stream()
                .map(AdminAiKnowledgeBaseLinkEntity::getSiteId)
                .toList();
        return new AdminAiKnowledgeBaseDto(
                base.getId(),
                base.getSiteId(),
                siteNames.get(base.getSiteId()),
                base.getName(),
                Boolean.TRUE.equals(base.getIsDefault()),
                Long.valueOf(viewerSiteId).equals(base.getSiteId()),
                count,
                linkedSiteIds,
                base.getCreatedAt(),
                base.getUpdatedAt());
    }

    /** 建档目标库：指定时须为本站自有库；未指定时落默认库（缺失则懒创建，覆盖迁移前的空站点）。 */
    private long resolveWritableBaseId(long siteId, Long requestedBaseId) {
        if (requestedBaseId != null) {
            return requireOwnedBase(siteId, requestedBaseId).getId();
        }
        AdminAiKnowledgeBaseEntity defaultBase = baseMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getSiteId, siteId)
                        .eq(AdminAiKnowledgeBaseEntity::getIsDefault, true));
        if (defaultBase != null) {
            return defaultBase.getId();
        }
        AdminAiKnowledgeBaseEntity created = new AdminAiKnowledgeBaseEntity();
        created.setSiteId(siteId);
        created.setName("默认知识库");
        created.setIsDefault(true);
        created.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(created);
        return created.getId();
    }

    private AdminAiKnowledgeBaseEntity requireOwnedBase(long siteId, long baseId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null || !Long.valueOf(siteId).equals(base.getSiteId())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        return base;
    }

    private AdminAiKnowledgeBaseEntity requireVisibleBase(long siteId, long baseId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        if (Long.valueOf(siteId).equals(base.getSiteId())) {
            return base;
        }
        AdminAiKnowledgeBaseLinkEntity link = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getSiteId, siteId));
        if (link == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        return base;
    }

    /** 文档的索引分区集合：所有者站点 + 该库被链接到的全部站点。 */
    private List<Long> partitionSiteIds(AdminAiKnowledgeDocumentEntity entity) {
        List<Long> targets = new ArrayList<>();
        targets.add(entity.getSiteId());
        if (entity.getBaseId() != null) {
            for (AdminAiKnowledgeBaseLinkEntity link :
                    linkMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                            .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, entity.getBaseId()))) {
                if (!targets.contains(link.getSiteId())) {
                    targets.add(link.getSiteId());
                }
            }
        }
        return targets;
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
        removeFromAllPartitions(entity);
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
            removeFromAllPartitions(document);
        }
        // 站点删除时，其名下知识库与共享链接一并清理（链接行的目标站点分区已随文档移除处理）
        List<AdminAiKnowledgeBaseEntity> bases = baseMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getSiteId, siteId));
        for (AdminAiKnowledgeBaseEntity base : bases) {
            linkMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                    .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, base.getId()));
            base.setDeletedAt(now);
            baseMapper.updateById(base);
            baseMapper.deleteById(base.getId());
        }
        linkMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                .eq(AdminAiKnowledgeBaseLinkEntity::getSiteId, siteId));
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
                    continue;
                }
                // 共享扇出：链接站点分区用各自的入口规格重嵌，失败计入 failed（以目标站点标注）
                for (Long target : partitionSiteIds(document)) {
                    if (target.equals(document.getSiteId())) {
                        continue;
                    }
                    try {
                        upsertDerivedIndexForSite(document, target);
                    } catch (RuntimeException ex) {
                        failed.add(new AdminAiKnowledgeReembedFailureDto(
                                target, sourceId(document.getId()), indexError(ex)));
                    }
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

    @Override
    public String transcribeScannedPdf(long siteId, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        List<AgentModelSelection> chain = modelEntryService.selectionChain(siteId);
        if (chain.isEmpty()) {
            log.info("KB OCR skipped for site {}: no usable model entry chain", siteId);
            return "";
        }
        try {
            AgentOcrPdfResult result =
                    agentClient.ocrPdf(
                            new AgentOcrPdfRequest(
                                    chain.get(0),
                                    chain.size() > 1 ? chain.subList(1, chain.size()) : List.of(),
                                    java.util.Base64.getEncoder().encodeToString(pdfBytes)));
            String text = result == null || result.text() == null ? "" : result.text().trim();
            log.info(
                    "KB OCR for site {} via {} produced {} chars",
                    siteId,
                    result == null ? null : result.modelSpec(),
                    text.length());
            return text;
        } catch (RuntimeException ex) {
            log.warn("KB OCR failed for site {}: {}", siteId, ex.getMessage());
            return "";
        }
    }

    @Override
    public String transcribeImage(long siteId, byte[] imageBytes, String mediaType) {
        if (imageBytes == null || imageBytes.length == 0 || mediaType == null || mediaType.isBlank()) {
            return "";
        }
        List<AgentModelSelection> chain = modelEntryService.selectionChain(siteId);
        if (chain.isEmpty()) {
            log.info("KB image OCR skipped for site {}: no usable model entry chain", siteId);
            return "";
        }
        try {
            AgentOcrPdfResult result =
                    agentClient.ocrImage(
                            new AgentOcrImageRequest(
                                    chain.get(0),
                                    chain.size() > 1 ? chain.subList(1, chain.size()) : List.of(),
                                    java.util.Base64.getEncoder().encodeToString(imageBytes),
                                    mediaType));
            String text = result == null || result.text() == null ? "" : result.text().trim();
            log.info(
                    "KB image OCR for site {} via {} produced {} chars",
                    siteId,
                    result == null ? null : result.modelSpec(),
                    text.length());
            return text;
        } catch (RuntimeException ex) {
            log.warn("KB image OCR failed for site {}: {}", siteId, ex.getMessage());
            return "";
        }
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
     * 文档同步到全部目标分区（所有者 + 共享链接站点）；落库的 index_status 以所有者分区结果为准，
     * 链接分区的失败只记日志（其健康在消费站点的实际检索中体现）。
     */
    private void syncDerivedIndex(AdminAiKnowledgeDocumentEntity entity) {
        List<Long> targets = partitionSiteIds(entity);
        if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            RuntimeException ownerFailure = null;
            try {
                upsertDerivedIndexForSite(entity, entity.getSiteId());
            } catch (RuntimeException ex) {
                ownerFailure = ex;
            }
            for (Long target : targets) {
                if (target.equals(entity.getSiteId())) {
                    continue;
                }
                try {
                    upsertDerivedIndexForSite(entity, target);
                } catch (RuntimeException ex) {
                    log.warn(
                            "KB fan-out sync failed (ownerSiteId={}, targetSiteId={}, documentId={});"
                                    + " linked partition may lag",
                            entity.getSiteId(),
                            target,
                            entity.getId(),
                            ex);
                }
            }
            if (ownerFailure == null) {
                recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null);
            } else {
                log.warn(
                        "KB derived index sync failed (siteId={}, documentId={}); index may lag",
                        entity.getSiteId(),
                        entity.getId(),
                        ownerFailure);
                recordIndexOutcome(
                        entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ownerFailure));
            }
        } else {
            for (Long target : targets) {
                try {
                    agentClient.deleteRetrievalDocument(String.valueOf(target), sourceId(entity.getId()));
                } catch (RuntimeException ex) {
                    log.warn(
                            "KB derived index delete failed (siteId={}, documentId={}); index may lag",
                            target,
                            entity.getId(),
                            ex);
                }
            }
            recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING, null);
        }
    }

    /** 以目标站点自己的模型入口规格 upsert 到其检索分区（共享文档在每个分区用消费站点的 embedding）。 */
    private void upsertDerivedIndexForSite(AdminAiKnowledgeDocumentEntity entity, long targetSiteId) {
        AgentModelSelection selection = modelEntryService.currentSelection(targetSiteId);
        agentClient.upsertRetrievalDocument(retrievalDocument(entity, targetSiteId, selection));
    }

    private static AgentRetrievalDocument retrievalDocument(
            AdminAiKnowledgeDocumentEntity entity, long targetSiteId, AgentModelSelection selection) {
        return new AgentRetrievalDocument(
                String.valueOf(targetSiteId),
                sourceId(entity.getId()),
                entity.getTitle(),
                entity.getContent(),
                selection == null ? null : selection.embeddingSpec(),
                selection == null ? null : selection.apiKey(),
                selection == null ? null : selection.baseUrl());
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
        agentClient.upsertRetrievalDocument(retrievalDocument(entity, entity.getSiteId(), selection));
    }

    /** 从文档的全部目标分区移除（所有者 + 共享链接站点），逐个 best-effort。 */
    private void removeFromAllPartitions(AdminAiKnowledgeDocumentEntity entity) {
        for (Long target : partitionSiteIds(entity)) {
            removeFromDerivedIndex(target, entity.getId());
        }
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

    private static AdminAiKnowledgeListItemDto toListItem(
            AdminAiKnowledgeDocumentEntity entity, long viewerSiteId) {
        return new AdminAiKnowledgeListItemDto(
                entity.getId(),
                entity.getSiteId(),
                entity.getBaseId(),
                Long.valueOf(viewerSiteId).equals(entity.getSiteId()),
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
                entity.getBaseId(),
                true,
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
