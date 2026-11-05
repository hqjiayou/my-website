package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeBaseDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeFailureDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeFileDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeOverviewDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeReindexFailedResultDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeAgentStatDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta;
import com.mywebsite.admin.entity.AdminAgentEntity;
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
import com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseLinkMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeRetrievalSyncIssueMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentDimensionChangeRequiredException;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.utils.AgentEmbeddingClassUtils;
import com.mywebsite.agent.dto.AgentOcrImageRequest;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentRetrievalSegment;
import com.mywebsite.agent.dto.AgentRetrievalTable;
import com.mywebsite.agent.dto.AgentRetrievalTableRow;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.mywebsite.infra.knowledge.KnowledgeContentConverter;
import com.mywebsite.infra.knowledge.KnowledgeTextExtractor;
import com.mywebsite.infra.knowledge.KnowledgeTextExtractor.ExtractionException;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final AdminAgentMapper adminAgentMapper;
    private final AgentClient agentClient;
    private final AdminAiModelEntryService modelEntryService;
    private final ObjectStorageService objectStorageService;
    private final StorageProperties storageProperties;
    private final AdminAiKnowledgeBaseMapper baseMapper;
    private final AdminAiKnowledgeBaseLinkMapper linkMapper;
    private final ExecutorService adminAiStreamExecutor;
    private final AdminAiKnowledgeRetrievalSyncIssueMapper syncIssueMapper;

    public AdminAiKnowledgeServiceImpl(
            AdminAiKnowledgeDocumentMapper knowledgeMapper,
            AdminAgentMapper adminAgentMapper,
            AgentClient agentClient,
            AdminAiModelEntryService modelEntryService,
            ObjectStorageService objectStorageService,
            StorageProperties storageProperties,
            AdminAiKnowledgeBaseMapper baseMapper,
            AdminAiKnowledgeBaseLinkMapper linkMapper,
            ExecutorService adminAiStreamExecutor,
            AdminAiKnowledgeRetrievalSyncIssueMapper syncIssueMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.agentClient = agentClient;
        this.modelEntryService = modelEntryService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.baseMapper = baseMapper;
        this.linkMapper = linkMapper;
        this.adminAiStreamExecutor = adminAiStreamExecutor;
        this.syncIssueMapper = syncIssueMapper;
    }

    @Override
    public PageResult<AdminAiKnowledgeListItemDto> listDocuments(
            long agentId, AdminAiKnowledgeListQuery query) {
        requireSite(agentId);
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
            requireVisibleBase(agentId, query.baseId());
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getBaseId, query.baseId());
        } else {
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getAgentId, agentId);
        }
        if (query != null && StringUtils.hasText(query.status())) {
            requireStatus(query.status());
            wrapper.eq(AdminAiKnowledgeDocumentEntity::getStatus, query.status());
        }

        Page<AdminAiKnowledgeDocumentEntity> page =
                knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, List<AgentModelSelection>> selectionCache = new LinkedHashMap<>();
        return new PageResult<>(
                page.getRecords().stream()
                        .map(entity -> toListItem(entity, agentId, isIndexStale(entity, selectionCache)))
                        .toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminAiKnowledgeDetailDto getDocument(long agentId, long documentId) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(agentId, documentId);
        return toDetail(entity, isIndexStale(entity, new LinkedHashMap<>()));
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto createDocument(long agentId, AdminAiKnowledgeCreateRequest request) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setAgentId(agentId);
        entity.setBaseId(resolveWritableBaseId(agentId, request.baseId()));
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        entity.setUnevaluatedFormulaCount(0);
        knowledgeMapper.insert(entity);

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(agentId, entity.getId());
        syncDerivedIndex(saved);
        return toDetail(saved, isIndexStale(saved, new LinkedHashMap<>()));
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto createUploadedDocument(
            long agentId, AdminAiKnowledgeCreateRequest request, AdminAiKnowledgeUploadMeta meta) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity duplicate = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AdminAiKnowledgeDocumentEntity::getSha256, meta.sha256()));
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.FILE_DUPLICATE);
        }

        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setAgentId(agentId);
        entity.setBaseId(resolveWritableBaseId(agentId, request.baseId()));
        entity.setTitle(request.title().trim());
        entity.setContent(storedUploadContent(request.content(), meta));
        entity.setUnevaluatedFormulaCount(unevaluatedFormulaCount(meta));
        entity.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        entity.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        entity.setSourceFormat(meta.sourceFormat());
        entity.setOriginalFilename(meta.originalFilename());
        entity.setFileSizeBytes(meta.fileSizeBytes());
        entity.setSha256(meta.sha256());

        // 存储已配置时先落原档（key 按内容指纹，天然幂等）；落档失败诚实拒绝整单，不假装存了。
        String bucket = storageProperties == null ? null : storageProperties.getBucket();
        if (StringUtils.hasText(bucket)) {
            String objectKey =
                    "sites/" + agentId + "/knowledge/" + meta.sha256() + "." + meta.sourceFormat();
            try {
                objectStorageService.put(
                        bucket,
                        objectKey,
                        new ByteArrayInputStream(meta.bytes()),
                        meta.bytes().length,
                        meta.contentType());
            } catch (RuntimeException ex) {
                log.warn("KB original file put failed (agentId={}, key={})", agentId, objectKey, ex);
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR.getCode(), AdminAiKnowledgeMessageKeys.FILE_STORAGE_FAILED);
            }
            entity.setStorageBucket(bucket);
            entity.setStorageKey(objectKey);
        }
        knowledgeMapper.insert(entity);

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(agentId, entity.getId());
        // 派生索引（embedding）改事务提交后异步跑，避免大 xlsm 同步卡住上传 HTTP（前端 60s 超时）。
        // 响应仍为 PENDING；列表可轮询到 INDEXED/FAILED，失败可点「重新索引」。
        scheduleDerivedIndexAfterCommit(agentId, saved.getId(), meta.bytes());
        return toDetail(saved, isIndexStale(saved, new LinkedHashMap<>()));
    }

    /**
     * 上传路径专用：事务提交后再异步 {@link #syncDerivedIndex}。
     * 任务内重载实体；文档已删／非 ACTIVE／不属于该站则直接退出，避免竞态写脏索引。
     */
    private void scheduleDerivedIndexAfterCommit(long agentId, long documentId, byte[] originalBytes) {
        byte[] bytesCopy = originalBytes == null ? null : originalBytes.clone();
        Runnable task = () -> {
            try {
                AdminAiKnowledgeDocumentEntity current = knowledgeMapper.selectById(documentId);
                if (current == null
                        || !Objects.equals(current.getAgentId(), agentId)
                        || !AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(current.getStatus())) {
                    return;
                }
                syncDerivedIndex(current, false, bytesCopy);
            } catch (RuntimeException ex) {
                log.warn(
                        "async KB derived index failed (agentId={}, documentId={})",
                        agentId,
                        documentId,
                        ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    adminAiStreamExecutor.execute(task);
                }
            });
            return;
        }
        adminAiStreamExecutor.execute(task);
    }

    @Override
    public AdminAiKnowledgeOverviewDto knowledgeOverview() {
        List<AdminAiKnowledgeDocumentEntity> documents =
                knowledgeMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> agentNames = new LinkedHashMap<>();
        for (AdminAgentEntity agent : adminAgentMapper.selectList(new LambdaQueryWrapper<>())) {
            agentNames.put(agent.getId(), agent.getAgentName());
        }
        long indexed = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(doc.getIndexStatus()))
                .count();
        long failed = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(doc.getIndexStatus()))
                .count();
        // 按 agentId 分组缓存 selection，避免逐篇重复查库（全平台聚合，agent 数远小于文档数）
        Map<Long, List<AgentModelSelection>> staleSelectionCache = new LinkedHashMap<>();
        long stale = documents.stream().filter(doc -> isIndexStale(doc, staleSelectionCache)).count();
        long originalBytes = documents.stream()
                .mapToLong(doc -> doc.getFileSizeBytes() == null ? 0L : doc.getFileSizeBytes())
                .sum();

        Map<Long, List<AdminAiKnowledgeDocumentEntity>> bySite = new LinkedHashMap<>();
        for (AdminAiKnowledgeDocumentEntity doc : documents) {
            bySite.computeIfAbsent(doc.getAgentId(), key -> new ArrayList<>()).add(doc);
        }
        List<AdminAiKnowledgeAgentStatDto> agentStats = new ArrayList<>();
        for (Map.Entry<Long, List<AdminAiKnowledgeDocumentEntity>> entry : bySite.entrySet()) {
            List<AdminAiKnowledgeDocumentEntity> agentDocs = entry.getValue();
            agentStats.add(new AdminAiKnowledgeAgentStatDto(
                    entry.getKey(),
                    agentNames.get(entry.getKey()),
                    agentDocs.size(),
                    agentDocs.stream()
                            .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(
                                    doc.getIndexStatus()))
                            .count(),
                    agentDocs.stream()
                            .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(
                                    doc.getIndexStatus()))
                            .count(),
                    agentDocs.stream()
                            .mapToLong(doc -> doc.getFileSizeBytes() == null ? 0L : doc.getFileSizeBytes())
                            .sum()));
        }
        agentStats.sort((a, b) -> a.agentId().compareTo(b.agentId()));

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
                        doc.getAgentId(),
                        agentNames.get(doc.getAgentId()),
                        doc.getTitle(),
                        doc.getIndexError(),
                        doc.getUpdatedAt()))
                .toList();

        // Issue #203：同步问题表让链接分区滞后与待删残留在总览可见（此前只存在于日志）。
        List<AdminAiKnowledgeRetrievalSyncIssueEntity> syncIssues = syncIssueMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity>());
        if (syncIssues == null) {
            syncIssues = List.of();
        }
        long laggingLinkPartitions = syncIssues.stream()
                .filter(issue -> AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT.equals(issue.getOp()))
                .count();
        long pendingRetrievalDeletes = syncIssues.stream()
                .filter(issue -> AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE.equals(issue.getOp()))
                .count();
        OffsetDateTime oldestSyncIssueAt = syncIssues.stream()
                .map(AdminAiKnowledgeRetrievalSyncIssueEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        return new AdminAiKnowledgeOverviewDto(
                documents.size(),
                indexed,
                failed,
                documents.size() - indexed - failed,
                stale,
                originalBytes,
                agentStats,
                recentFailures,
                laggingLinkPartitions,
                pendingRetrievalDeletes,
                oldestSyncIssueAt);
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
    public AdminAiKnowledgeFileDto originalFileUrl(long agentId, long documentId) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity entity = knowledgeMapper.selectById(documentId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Long.valueOf(agentId).equals(entity.getAgentId())) {
            // 非所有者：仅当文档所在知识库共享给本站时可见
            if (entity.getBaseId() == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            requireVisibleBase(agentId, entity.getBaseId());
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
    public List<AdminAiKnowledgeBaseDto> listBasesForSite(long agentId) {
        requireSite(agentId);
        List<AdminAiKnowledgeBaseEntity> visible = new ArrayList<>();
        visible.addAll(baseMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                .eq(AdminAiKnowledgeBaseEntity::getAgentId, agentId)
                .orderByAsc(AdminAiKnowledgeBaseEntity::getId)));
        List<AdminAiKnowledgeBaseLinkEntity> links = linkMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getAgentId, agentId));
        for (AdminAiKnowledgeBaseLinkEntity link : links) {
            AdminAiKnowledgeBaseEntity base = baseMapper.selectById(link.getBaseId());
            if (base != null) {
                visible.add(base);
            }
        }
        Map<Long, String> agentNames = agentNames();
        return visible.stream().map(base -> toBaseDto(base, agentId, agentNames)).toList();
    }

    @Override
    public List<AdminAiKnowledgeBaseDto> listAllBases() {
        Map<Long, String> agentNames = agentNames();
        return baseMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getAgentId)
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getId))
                .stream()
                .map(base -> toBaseDto(base, base.getAgentId(), agentNames))
                .toList();
    }

    @Override
    @Transactional
    public AdminAiKnowledgeBaseDto createBase(long agentId, AdminAiKnowledgeBaseCreateRequest request) {
        requireSite(agentId);
        AdminAiKnowledgeBaseEntity base = new AdminAiKnowledgeBaseEntity();
        base.setAgentId(agentId);
        base.setName(request.name().trim());
        base.setIsDefault(false);
        base.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(base);
        return toBaseDto(baseMapper.selectById(base.getId()), agentId, agentNames());
    }

    @Override
    @Transactional
    public void deleteBase(long agentId, long baseId) {
        requireSite(agentId);
        AdminAiKnowledgeBaseEntity base = requireOwnedBase(agentId, baseId);
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
    public void linkBase(long baseId, long targetAgentId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        requireSite(targetAgentId);
        if (Long.valueOf(targetAgentId).equals(base.getAgentId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.BASE_LINK_SELF);
        }
        AdminAiKnowledgeBaseLinkEntity existing = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getAgentId, targetAgentId));
        if (existing != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.BASE_ALREADY_LINKED);
        }
        AdminAiKnowledgeBaseLinkEntity link = new AdminAiKnowledgeBaseLinkEntity();
        link.setBaseId(baseId);
        link.setAgentId(targetAgentId);
        linkMapper.insert(link);
        // 扇出：该库现存 ACTIVE 文档同步进目标站点分区（失败记日志并落同步问题行，Issue #203）
        for (AdminAiKnowledgeDocumentEntity document : activeDocumentsOfBase(baseId)) {
            try {
                upsertLinkPartition(document, targetAgentId);
            } catch (RuntimeException ex) {
                log.warn(
                        "KB link fan-out upsert failed (baseId={}, targetAgentId={}, documentId={})",
                        baseId,
                        targetAgentId,
                        document.getId(),
                        ex);
            }
        }
    }

    @Override
    @Transactional
    public void unlinkBase(long baseId, long targetAgentId) {
        AdminAiKnowledgeBaseLinkEntity existing = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getAgentId, targetAgentId));
        if (existing == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        linkMapper.deleteById(existing.getId());
        for (AdminAiKnowledgeDocumentEntity document : activeDocumentsOfBase(baseId)) {
            try {
                deleteFromPartition(targetAgentId, sourceId(document.getId()));
            } catch (RuntimeException ex) {
                log.warn(
                        "KB unlink fan-out delete failed (baseId={}, targetAgentId={}, documentId={})",
                        baseId,
                        targetAgentId,
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

    private Map<Long, String> agentNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (AdminAgentEntity agent : adminAgentMapper.selectList(new LambdaQueryWrapper<>())) {
            names.put(agent.getId(), agent.getAgentName());
        }
        return names;
    }

    private AdminAiKnowledgeBaseDto toBaseDto(
            AdminAiKnowledgeBaseEntity base, long viewerAgentId, Map<Long, String> agentNames) {
        long count = knowledgeMapper.selectCount(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                .eq(AdminAiKnowledgeDocumentEntity::getBaseId, base.getId()));
        List<Long> linkedAgentIds = linkMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, base.getId()))
                .stream()
                .map(AdminAiKnowledgeBaseLinkEntity::getAgentId)
                .toList();
        return new AdminAiKnowledgeBaseDto(
                base.getId(),
                base.getAgentId(),
                agentNames.get(base.getAgentId()),
                base.getName(),
                Boolean.TRUE.equals(base.getIsDefault()),
                Long.valueOf(viewerAgentId).equals(base.getAgentId()),
                count,
                linkedAgentIds,
                base.getCreatedAt(),
                base.getUpdatedAt());
    }

    /** 建档目标库：指定时须为本站自有库；未指定时落默认库（缺失则懒创建，覆盖迁移前的空站点）。 */
    private long resolveWritableBaseId(long agentId, Long requestedBaseId) {
        if (requestedBaseId != null) {
            return requireOwnedBase(agentId, requestedBaseId).getId();
        }
        AdminAiKnowledgeBaseEntity defaultBase = baseMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getAgentId, agentId)
                        .eq(AdminAiKnowledgeBaseEntity::getIsDefault, true));
        if (defaultBase != null) {
            return defaultBase.getId();
        }
        AdminAiKnowledgeBaseEntity created = new AdminAiKnowledgeBaseEntity();
        created.setAgentId(agentId);
        created.setName("默认知识库");
        created.setIsDefault(true);
        created.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(created);
        return created.getId();
    }

    private AdminAiKnowledgeBaseEntity requireOwnedBase(long agentId, long baseId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null || !Long.valueOf(agentId).equals(base.getAgentId())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        return base;
    }

    private AdminAiKnowledgeBaseEntity requireVisibleBase(long agentId, long baseId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        if (Long.valueOf(agentId).equals(base.getAgentId())) {
            return base;
        }
        AdminAiKnowledgeBaseLinkEntity link = linkMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                        .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseLinkEntity::getAgentId, agentId));
        if (link == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        return base;
    }

    /** 文档的索引分区集合：所有者站点 + 该库被链接到的全部站点。 */
    private List<Long> partitionAgentIds(AdminAiKnowledgeDocumentEntity entity) {
        List<Long> targets = new ArrayList<>();
        targets.add(entity.getAgentId());
        if (entity.getBaseId() != null) {
            for (AdminAiKnowledgeBaseLinkEntity link :
                    linkMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                            .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, entity.getBaseId()))) {
                if (!targets.contains(link.getAgentId())) {
                    targets.add(link.getAgentId());
                }
            }
        }
        return targets;
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto updateDocument(
            long agentId, long documentId, AdminAiKnowledgeUpdateRequest request) {
        requireSite(agentId);
        requireStatus(request.status());
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(agentId, documentId);
        boolean contentUnchanged = Objects.equals(entity.getTitle(), request.title().trim())
                && Objects.equals(entity.getContent(), request.content());
        entity.setTitle(request.title().trim());
        entity.setContent(request.content());
        entity.setStatus(request.status());
        entity.setVersion(request.version());
        int updated = knowledgeMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        AdminAiKnowledgeDocumentEntity saved = requireDocumentInSite(agentId, documentId);
        syncDerivedIndex(saved, contentUnchanged);
        return toDetail(saved, isIndexStale(saved, new LinkedHashMap<>()));
    }

    @Override
    @Transactional
    public AdminAiKnowledgeDetailDto reindexDocument(long agentId, long documentId) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(agentId, documentId);
        if (!AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        syncDerivedIndex(entity, true);
        return toDetail(entity, isIndexStale(entity, new LinkedHashMap<>()));
    }

    @Override
    @Transactional
    public void deleteDocument(long agentId, long documentId) {
        requireSite(agentId);
        AdminAiKnowledgeDocumentEntity entity = requireDocumentInSite(agentId, documentId);
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
                    "KB original file remove failed (agentId={}, documentId={}); object may linger",
                    entity.getAgentId(),
                    entity.getId(),
                    ex);
        }
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getAgentId, agentId));
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
                        .eq(AdminAiKnowledgeBaseEntity::getAgentId, agentId));
        for (AdminAiKnowledgeBaseEntity base : bases) {
            linkMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                    .eq(AdminAiKnowledgeBaseLinkEntity::getBaseId, base.getId()));
            base.setDeletedAt(now);
            baseMapper.updateById(base);
            baseMapper.deleteById(base.getId());
        }
        linkMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseLinkEntity>()
                .eq(AdminAiKnowledgeBaseLinkEntity::getAgentId, agentId));
    }

    @Override
    public AdminAiKnowledgeReembedResultDto reembedAll() {
        List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getAgentId)
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getId));
        Map<Long, List<AdminAiKnowledgeDocumentEntity>> documentsBySite = new LinkedHashMap<>();
        for (AdminAiKnowledgeDocumentEntity document : documents) {
            if (document.getAgentId() == null || document.getId() == null) {
                continue;
            }
            documentsBySite.computeIfAbsent(document.getAgentId(), ignored -> new ArrayList<>()).add(document);
        }
        if (documentsBySite.isEmpty()) {
            return new AdminAiKnowledgeReembedResultDto(0, 0, null, false, List.of(), REEMBED_SUCCESS);
        }

        Map<Long, List<AgentModelSelection>> classesBySite = new LinkedHashMap<>();
        boolean multiClass = false;
        for (Long agentId : documentsBySite.keySet()) {
            List<AgentModelSelection> classes = registeredClasses(agentId);
            classesBySite.put(agentId, classes);
            if (classes.size() > 1) {
                multiClass = true;
            }
        }

        Integer dimensions = null;
        boolean recreatedSchema = false;
        // 多类的表由 upsert 按向量长度另建。confirm 重建只会 DROP 遗留 chunks，同维度的其它类会被清掉。
        // 有站点登记了两类及以上时，整批都不走这条重建。
        if (!multiClass) {
            Map<SelectionGroup, List<Long>> sitesBySelection = new LinkedHashMap<>();
            for (Map.Entry<Long, List<AgentModelSelection>> site : classesBySite.entrySet()) {
                AgentModelSelection selection = site.getValue().isEmpty() ? null : site.getValue().get(0);
                sitesBySelection
                        .computeIfAbsent(new SelectionGroup(selection), ignored -> new ArrayList<>())
                        .add(site.getKey());
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
                for (Long agentId : group.getValue()) {
                    dimensionsBySite.put(agentId, probe.dimensions());
                }
            }
            if (new LinkedHashSet<>(dimensionsBySite.values()).size() > 1) {
                List<AdminAiKnowledgeReembedConflictDto> conflicts = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : dimensionsBySite.entrySet()) {
                    conflicts.add(new AdminAiKnowledgeReembedConflictDto(entry.getKey(), entry.getValue()));
                }
                throw new ReembedConflictException(conflicts);
            }
            dimensions = dimensionsBySite.values().iterator().next();
            if (columnDimensions == null || !columnDimensions.equals(dimensions)) {
                AgentReembedPrepareResult recreated = agentClient.prepareReembed(confirmSelection, true);
                dimensions = recreated.dimensions();
                recreatedSchema = recreated.recreated();
            }
        }

        List<AdminAiKnowledgeReembedFailureDto> failed = new ArrayList<>();
        int attempted = 0;
        for (Map.Entry<Long, List<AdminAiKnowledgeDocumentEntity>> agent : documentsBySite.entrySet()) {
            List<AgentModelSelection> classes = classesBySite.get(agent.getKey());
            for (AdminAiKnowledgeDocumentEntity document : agent.getValue()) {
                if (!AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(document.getStatus())) {
                    continue;
                }
                attempted++;
                try {
                    upsertDerivedIndex(document, classes);
                    recordIndexOutcome(
                            document, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null, classes);
                } catch (RuntimeException ex) {
                    recordIndexOutcome(document, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ex));
                    failed.add(new AdminAiKnowledgeReembedFailureDto(
                            document.getAgentId(), sourceId(document.getId()), indexError(ex)));
                    continue;
                }
                // 共享扇出：链接站点分区用各自的入口规格重嵌，失败计入 failed（以目标站点标注）
                // 并落同步问题行待重试（Issue #203）
                for (Long target : partitionAgentIds(document)) {
                    if (target.equals(document.getAgentId())) {
                        continue;
                    }
                    try {
                        upsertLinkPartition(document, target);
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

    /**
     * OCR 转写链的模式口径（Issue #145 F4a）：先 KEY 链、空了才 CLI 链。
     * 不再用不分模式的整链——双模式各有默认时整链排序只剩 id 序，烧哪边的
     * 配额不可预测。转写是后台批处理，直连 Key 可预测优先，代理兜底。
     */
    private List<AgentModelSelection> ocrChain(long agentId) {
        List<AgentModelSelection> chain = modelEntryService.selectionChain(agentId, "KEY");
        return chain.isEmpty() ? modelEntryService.selectionChain(agentId, "CLI") : chain;
    }

    @Override
    public String transcribeScannedPdf(long agentId, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        List<AgentModelSelection> chain = ocrChain(agentId);
        if (chain.isEmpty()) {
            log.info("KB OCR skipped for agent {}: no usable model entry chain", agentId);
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
                    "KB OCR for agent {} via {} produced {} chars",
                    agentId,
                    result == null ? null : result.modelSpec(),
                    text.length());
            return text;
        } catch (RuntimeException ex) {
            log.warn("KB OCR failed for agent {}: {}", agentId, ex.getMessage());
            return "";
        }
    }

    @Override
    public String transcribeImage(long agentId, byte[] imageBytes, String mediaType) {
        if (imageBytes == null || imageBytes.length == 0 || mediaType == null || mediaType.isBlank()) {
            return "";
        }
        List<AgentModelSelection> chain = ocrChain(agentId);
        if (chain.isEmpty()) {
            log.info("KB image OCR skipped for agent {}: no usable model entry chain", agentId);
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
                    "KB image OCR for agent {} via {} produced {} chars",
                    agentId,
                    result == null ? null : result.modelSpec(),
                    text.length());
            return text;
        } catch (RuntimeException ex) {
            log.warn("KB image OCR failed for agent {}: {}", agentId, ex.getMessage());
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
        syncDerivedIndex(entity, false, null);
    }

    private void syncDerivedIndex(AdminAiKnowledgeDocumentEntity entity, boolean allowFillMissing) {
        syncDerivedIndex(entity, allowFillMissing, null);
    }

    /**
     * {@code allowFillMissing} 为真且正文没变、文档已是 INDEXED、记下的类名是当前登记的真子集时，
     * 只补缺的类。正文变了必须整份重写。失败不把文档标成 INDEXED，已记下的类名保留。
     */
    private void syncDerivedIndex(
            AdminAiKnowledgeDocumentEntity entity, boolean allowFillMissing, byte[] originalBytes) {
        List<Long> targets = partitionAgentIds(entity);
        DerivedIndexParts parts = derivedIndexParts(entity, originalBytes);
        if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            List<AgentModelSelection> ownerClasses = registeredClasses(entity.getAgentId());
            List<AgentModelSelection> toWrite = classesToWrite(entity, ownerClasses, allowFillMissing);
            // Issue #202 UoW-2：只有 toWrite 就是完整登记类集时，Python 才可按差集清理
            // 已注销类的残留行；fill-missing 子集补写（真子集）时 complete=false，不清理。
            boolean classesComplete = Objects.equals(
                    AgentEmbeddingClassUtils.canonical(toWrite),
                    AgentEmbeddingClassUtils.canonical(ownerClasses));
            RuntimeException ownerFailure = null;
            try {
                upsertDerivedIndexForSite(entity, entity.getAgentId(), toWrite, parts, classesComplete);
            } catch (RuntimeException ex) {
                ownerFailure = ex;
            }
            for (Long target : targets) {
                if (target.equals(entity.getAgentId())) {
                    continue;
                }
                try {
                    upsertLinkPartition(entity, target, parts);
                } catch (RuntimeException ex) {
                    log.warn(
                            "KB fan-out sync failed (ownerAgentId={}, targetAgentId={}, documentId={});"
                                    + " linked partition may lag",
                            entity.getAgentId(),
                            target,
                            entity.getId(),
                            ex);
                }
            }
            if (ownerFailure == null) {
                recordIndexOutcome(
                        entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null, ownerClasses);
            } else {
                log.warn(
                        "KB derived index sync failed (agentId={}, documentId={}); index may lag",
                        entity.getAgentId(),
                        entity.getId(),
                        ownerFailure);
                recordIndexOutcome(
                        entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ownerFailure));
            }
        } else {
            for (Long target : targets) {
                try {
                    deleteFromPartition(target, sourceId(entity.getId()));
                } catch (RuntimeException ex) {
                    log.warn(
                            "KB derived index delete failed (agentId={}, documentId={}); index may lag",
                            target,
                            entity.getId(),
                            ex);
                }
            }
            recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING, null);
        }
    }

    /** 以目标站点已登记的嵌入类 upsert（完整类集，complete=true）。没有则不带凭证，由进程嵌入模型写入。不跟随对话主用入口。 */
    private void upsertDerivedIndexForSite(
            AdminAiKnowledgeDocumentEntity entity, long targetAgentId, DerivedIndexParts parts) {
        upsertDerivedIndexForSite(entity, targetAgentId, registeredClasses(targetAgentId), parts, true);
    }

    private void upsertDerivedIndexForSite(
            AdminAiKnowledgeDocumentEntity entity,
            long targetAgentId,
            List<AgentModelSelection> classes,
            DerivedIndexParts parts,
            boolean classesComplete) {
        agentClient.upsertRetrievalDocument(
                retrievalDocument(entity, targetAgentId, classes, parts, classesComplete));
        // Issue #203 复评三审计：写入成功即作废同分区同文档的待删 tombstone——
        // 否则文档停用期落下的 DELETE 行会在文档恢复 ACTIVE 并重写后被重试任务
        // 执行，把活向量删掉（文档 INDEXED 却检索不到）。
        clearSyncIssue(
                targetAgentId, sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE);
    }

    private static AgentRetrievalDocument retrievalDocument(
            AdminAiKnowledgeDocumentEntity entity,
            long targetAgentId,
            List<AgentModelSelection> classes,
            DerivedIndexParts parts,
            boolean classesComplete) {
        List<AgentModelSelection> registered = classes == null ? List.of() : classes;
        AgentModelSelection first = registered.isEmpty() ? null : registered.get(0);
        List<AgentRetrievalSegment> segments = parts == null ? null : parts.segments();
        List<AgentRetrievalTable> tables = parts == null ? null : parts.tables();
        return new AgentRetrievalDocument(
                String.valueOf(targetAgentId),
                sourceId(entity.getId()),
                entity.getTitle(),
                entity.getContent(),
                first == null ? null : first.embeddingSpec(),
                first == null ? null : first.apiKey(),
                first == null ? null : first.baseUrl(),
                AgentEmbeddingClassUtils.wire(registered),
                segments == null || segments.isEmpty() ? null : segments,
                tables == null || tables.isEmpty() ? null : tables,
                // false 时不写字段（NON_NULL 省略），与旧载荷逐字一致，旧版 Python 不受影响。
                classesComplete ? Boolean.TRUE : null);
    }

    private void upsertDerivedIndexForSite(AdminAiKnowledgeDocumentEntity entity, long targetAgentId) {
        upsertDerivedIndexForSite(entity, targetAgentId, derivedIndexParts(entity, null));
    }

    private List<AgentModelSelection> registeredClasses(long agentId) {
        List<AgentModelSelection> classes = modelEntryService.embeddingClasses(agentId);
        return classes == null ? List.of() : classes;
    }

    private static List<AgentModelSelection> classesToWrite(
            AdminAiKnowledgeDocumentEntity entity,
            List<AgentModelSelection> ownerClasses,
            boolean allowFillMissing) {
        if (allowFillMissing
                && AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(entity.getIndexStatus())
                && AgentEmbeddingClassUtils.isProperSubset(entity.getIndexEmbeddingSpec(), ownerClasses)) {
            return AgentEmbeddingClassUtils.missing(entity.getIndexEmbeddingSpec(), ownerClasses);
        }
        return ownerClasses;
    }

    /** FAILED/PENDING 场景不涉及所有者 selection，走不带 selection 的重载（FAILED 保留旧 spec，PENDING 清空）。 */
    private void recordIndexOutcome(
            AdminAiKnowledgeDocumentEntity entity, String indexStatus, String indexError) {
        recordIndexOutcome(entity, indexStatus, indexError, null);
    }

    /**
     * 落库索引结果（定向更新四列，不动乐观锁 version）：INDEXED 时写 indexedAt、清 error，
     * 并把 indexEmbeddingSpec 落为当次所有者分区已登记的嵌入类名（没有登记时写 NULL）；
     * FAILED 时保留上次 indexedAt/indexEmbeddingSpec 只记 error；
     * PENDING（未在索引中）清 indexedAt/indexEmbeddingSpec 两者。
     * 同步更新内存实体，让本次请求的返回 DTO 立刻反映新状态。
     */
    private void recordIndexOutcome(
            AdminAiKnowledgeDocumentEntity entity,
            String indexStatus,
            String indexError,
            List<AgentModelSelection> ownerClasses) {
        boolean indexed = AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(indexStatus);
        boolean pending = AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING.equals(indexStatus);
        OffsetDateTime indexedAt = indexed ? OffsetDateTime.now(ZoneOffset.UTC) : pending ? null : entity.getIndexedAt();
        String embeddingSpec = indexed
                ? AgentEmbeddingClassUtils.canonical(ownerClasses)
                : pending ? null : entity.getIndexEmbeddingSpec();
        String error =
                indexError == null ? null : indexError.substring(0, Math.min(indexError.length(), 500));
        knowledgeMapper.update(
                null,
                new LambdaUpdateWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getId, entity.getId())
                        .set(AdminAiKnowledgeDocumentEntity::getIndexStatus, indexStatus)
                        .set(AdminAiKnowledgeDocumentEntity::getIndexError, error)
                        .set(AdminAiKnowledgeDocumentEntity::getIndexedAt, indexedAt)
                        .set(AdminAiKnowledgeDocumentEntity::getIndexEmbeddingSpec, embeddingSpec));
        entity.setIndexStatus(indexStatus);
        entity.setIndexError(error);
        entity.setIndexedAt(indexedAt);
        entity.setIndexEmbeddingSpec(embeddingSpec);
    }

    /**
     * 文档 {@code indexStatus == INDEXED} 且记下的类名与当前已登记的类不一致时为 stale。
     * 新登记一类也算。非 INDEXED 文档恒为 false。按 agentId 缓存，供列表和总览复用。
     */
    private boolean isIndexStale(
            AdminAiKnowledgeDocumentEntity entity, Map<Long, List<AgentModelSelection>> selectionCache) {
        if (!AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(entity.getIndexStatus())) {
            return false;
        }
        List<AgentModelSelection> current = selectionCache.computeIfAbsent(entity.getAgentId(), this::registeredClasses);
        return !AgentEmbeddingClassUtils.matchesStored(entity.getIndexEmbeddingSpec(), current);
    }

    private void removeFromDerivedIndex(long agentId, long documentId) {
        try {
            deleteFromPartition(agentId, sourceId(documentId));
        } catch (RuntimeException ex) {
            log.warn(
                    "KB derived index delete failed (agentId={}, documentId={}); index may lag",
                    agentId,
                    documentId,
                    ex);
        }
    }

    // ------------------------------------------------------------------
    // Issue #203：派生索引同步问题（落行＋定时重试），所有者分区不入表
    // ------------------------------------------------------------------

    private static LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity> syncIssueKey(
            long agentId, String sourceId, String op) {
        return new LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity>()
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getAgentId, agentId)
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getSourceId, sourceId)
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getOp, op);
    }

    /** 落/更新同步问题行。落行本身失败只记日志，不能掩盖原始同步结果。 */
    private void recordSyncIssue(long agentId, String sourceId, String op, RuntimeException failure) {
        try {
            AdminAiKnowledgeRetrievalSyncIssueEntity existing =
                    syncIssueMapper.selectOne(syncIssueKey(agentId, sourceId, op));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (existing == null) {
                AdminAiKnowledgeRetrievalSyncIssueEntity issue =
                        new AdminAiKnowledgeRetrievalSyncIssueEntity();
                issue.setAgentId(agentId);
                issue.setSourceId(sourceId);
                issue.setOp(op);
                issue.setAttempts(0);
                issue.setLastError(indexError(failure));
                issue.setCreatedAt(now);
                issue.setUpdatedAt(now);
                syncIssueMapper.insert(issue);
            } else {
                existing.setAttempts(existing.getAttempts() == null ? 1 : existing.getAttempts() + 1);
                existing.setLastError(indexError(failure));
                existing.setUpdatedAt(now);
                syncIssueMapper.updateById(existing);
            }
        } catch (RuntimeException recordFailure) {
            log.warn(
                    "KB retrieval sync issue record failed (agentId={}, sourceId={}, op={})",
                    agentId,
                    sourceId,
                    op,
                    recordFailure);
        }
    }

    /** 同步成功后清掉对应的问题行；清行失败只记日志。 */
    private void clearSyncIssue(long agentId, String sourceId, String op) {
        try {
            syncIssueMapper.delete(syncIssueKey(agentId, sourceId, op));
        } catch (RuntimeException clearFailure) {
            log.warn(
                    "KB retrieval sync issue clear failed (agentId={}, sourceId={}, op={})",
                    agentId,
                    sourceId,
                    op,
                    clearFailure);
        }
    }

    /** 链接分区扇出 upsert：成功清 op=UPSERT 问题行，失败落行并原样抛回（调用方维持原日志/计数语义）。 */
    private void upsertLinkPartition(AdminAiKnowledgeDocumentEntity entity, long targetAgentId) {
        try {
            upsertDerivedIndexForSite(entity, targetAgentId);
            clearSyncIssue(
                    targetAgentId, sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT);
        } catch (RuntimeException ex) {
            recordSyncIssue(
                    targetAgentId, sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT, ex);
            throw ex;
        }
    }

    private void upsertLinkPartition(
            AdminAiKnowledgeDocumentEntity entity, long targetAgentId, DerivedIndexParts parts) {
        try {
            upsertDerivedIndexForSite(entity, targetAgentId, parts);
            clearSyncIssue(
                    targetAgentId, sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT);
        } catch (RuntimeException ex) {
            recordSyncIssue(
                    targetAgentId, sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT, ex);
            throw ex;
        }
    }

    /** 分区删除：成功清 op=DELETE 与 op=UPSERT 问题行（删除取代待同步），失败落 tombstone 行并原样抛回。 */
    private void deleteFromPartition(long agentId, String sourceId) {
        try {
            agentClient.deleteRetrievalDocument(String.valueOf(agentId), sourceId);
            clearSyncIssue(agentId, sourceId, AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE);
            clearSyncIssue(agentId, sourceId, AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT);
        } catch (RuntimeException ex) {
            recordSyncIssue(agentId, sourceId, AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE, ex);
            throw ex;
        }
    }

    /**
     * 定时重试同步问题行（Issue #203）：DELETE 重删、UPSERT 重同步链接分区。
     * 已被业务变化取代的行（文档已删/已解链/非 ACTIVE/source 格式异常）直接作废删行；
     * 重试再失败只累加 attempts，留下轮。单行失败不影响其它行。
     */
    @Override
    @Scheduled(fixedDelayString = "${admin.knowledge.sync-issue-fixed-delay-ms:900000}")
    public void retryRetrievalSyncIssues() {
        List<AdminAiKnowledgeRetrievalSyncIssueEntity> issues = syncIssueMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity>()
                        .orderByAsc(AdminAiKnowledgeRetrievalSyncIssueEntity::getCreatedAt)
                        .last("LIMIT 200"));
        if (issues == null) {
            return;
        }
        for (AdminAiKnowledgeRetrievalSyncIssueEntity issue : issues) {
            try {
                if (AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE.equals(issue.getOp())) {
                    // 复评三审计第二层防护：落行后文档又恢复 ACTIVE 且仍属该分区时，
                    // 这条 tombstone 已被后续重写取代，作废不执行（与 upsert 成功清行互为冗余）。
                    if (!deleteIssueSuperseded(issue)) {
                        agentClient.deleteRetrievalDocument(
                                String.valueOf(issue.getAgentId()), issue.getSourceId());
                    }
                    syncIssueMapper.deleteById(issue.getId());
                } else if (AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT.equals(issue.getOp())) {
                    if (retryLinkPartitionUpsert(issue)) {
                        syncIssueMapper.deleteById(issue.getId());
                    } else {
                        bumpSyncIssue(issue, null);
                    }
                } else {
                    // 未知 op：留行待人工处理，只累加次数以便在总览/表里可见异常
                    bumpSyncIssue(issue, null);
                }
            } catch (RuntimeException ex) {
                bumpSyncIssue(issue, ex);
            }
        }
    }

    /** tombstone 是否已被后续重写取代：文档存在、ACTIVE 且该分区仍在文档的目标分区内。 */
    private boolean deleteIssueSuperseded(AdminAiKnowledgeRetrievalSyncIssueEntity issue) {
        Long documentId = parseSourceDocumentId(issue.getSourceId());
        if (documentId == null) {
            return false;
        }
        AdminAiKnowledgeDocumentEntity document = knowledgeMapper.selectById(documentId);
        return document != null
                && AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(document.getStatus())
                && partitionAgentIds(document).contains(issue.getAgentId());
    }

    /** @return true 表示该行可以删（已同步成功，或已被业务变化取代）；false 表示文档暂不可同步，留下轮。 */
    private boolean retryLinkPartitionUpsert(AdminAiKnowledgeRetrievalSyncIssueEntity issue) {
        Long documentId = parseSourceDocumentId(issue.getSourceId());
        if (documentId == null) {
            return true;
        }
        AdminAiKnowledgeDocumentEntity document = knowledgeMapper.selectById(documentId);
        if (document == null
                || !AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(document.getStatus())
                || !partitionAgentIds(document).contains(issue.getAgentId())) {
            return true;
        }
        upsertDerivedIndexForSite(document, issue.getAgentId());
        return true;
    }

    private void bumpSyncIssue(AdminAiKnowledgeRetrievalSyncIssueEntity issue, RuntimeException failure) {
        try {
            issue.setAttempts(issue.getAttempts() == null ? 1 : issue.getAttempts() + 1);
            if (failure != null) {
                issue.setLastError(indexError(failure));
            }
            issue.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            syncIssueMapper.updateById(issue);
        } catch (RuntimeException bumpFailure) {
            log.warn(
                    "KB retrieval sync issue bump failed (issueId={})", issue.getId(), bumpFailure);
        }
    }

    private static Long parseSourceDocumentId(String sourceId) {
        if (sourceId == null || !sourceId.startsWith("kb:")) {
            return null;
        }
        try {
            return Long.parseLong(sourceId.substring("kb:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void upsertDerivedIndex(AdminAiKnowledgeDocumentEntity entity, List<AgentModelSelection> classes) {
        // reembedAll 传入的是该站完整登记类集，complete=true。
        agentClient.upsertRetrievalDocument(retrievalDocument(
                entity, entity.getAgentId(), classes, derivedIndexParts(entity, null), true));
    }

    /**
     * 只有正文与原件重抽结果一致时才带位置段和结构化表。手改过的正文、读不到原件、或抽取失败时都不带，
     * 也不改 index 状态，避免把未改动文档判成需要全量重嵌。tables 不改写 entity.content。
     */
    private record DerivedIndexParts(
            List<AgentRetrievalSegment> segments, List<AgentRetrievalTable> tables) {}

    private DerivedIndexParts derivedIndexParts(AdminAiKnowledgeDocumentEntity entity, byte[] originalBytes) {
        String format = entity.getSourceFormat();
        if (!StringUtils.hasText(format) || !StringUtils.hasText(entity.getContent())) {
            return new DerivedIndexParts(null, null);
        }
        byte[] bytes = originalBytes != null ? originalBytes : readOriginal(entity);
        if (bytes == null || bytes.length == 0) {
            return new DerivedIndexParts(null, null);
        }
        try {
            if ("pdf".equals(format)) {
                List<KnowledgeTextExtractor.PageText> pages = KnowledgeTextExtractor.pages(bytes);
                if (!KnowledgeTextExtractor.pagedMarkdown(pages).equals(entity.getContent())) {
                    return new DerivedIndexParts(null, null);
                }
                List<AgentRetrievalSegment> segments = new ArrayList<>();
                for (KnowledgeTextExtractor.PageText page : pages) {
                    String body = page.text() == null ? "" : page.text().strip();
                    if (body.isEmpty()) {
                        continue;
                    }
                    segments.add(new AgentRetrievalSegment(
                            "## 第 " + page.page() + " 页\n\n" + body, page.page(), null, null, null));
                }
                return new DerivedIndexParts(segments.isEmpty() ? null : List.copyOf(segments), null);
            }
            if ("xlsx".equals(format) || "xlsm".equals(format) || "xls".equals(format)) {
                KnowledgeContentConverter.SpreadsheetExtraction extraction =
                        KnowledgeContentConverter.readSpreadsheet(bytes);
                if (!extraction.markdown().equals(entity.getContent())) {
                    return new DerivedIndexParts(null, null);
                }
                List<AgentRetrievalSegment> segments = new ArrayList<>();
                for (KnowledgeContentConverter.IndexSegment segment : extraction.segments()) {
                    segments.add(new AgentRetrievalSegment(
                            segment.text(), segment.page(), segment.sheet(), segment.rowStart(), segment.rowEnd()));
                }
                return new DerivedIndexParts(
                        segments.isEmpty() ? null : List.copyOf(segments), sheetTables(extraction.tables()));
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "KB location segments skipped (agentId={}, documentId={})",
                    entity.getAgentId(),
                    entity.getId(),
                    ex);
        }
        return new DerivedIndexParts(null, null);
    }

    private static List<AgentRetrievalTable> sheetTables(
            List<KnowledgeContentConverter.SheetTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        List<AgentRetrievalTable> out = new ArrayList<>();
        for (KnowledgeContentConverter.SheetTable table : tables) {
            List<AgentRetrievalTableRow> rows = new ArrayList<>();
            for (KnowledgeContentConverter.SheetRow row : table.rows()) {
                rows.add(new AgentRetrievalTableRow(row.rowNumber(), row.cells()));
            }
            out.add(new AgentRetrievalTable(
                    table.sheet(), table.headerRow(), table.headers(), List.copyOf(rows), table.truncated()));
        }
        return List.copyOf(out);
    }

    private static int unevaluatedFormulaCount(AdminAiKnowledgeUploadMeta meta) {
        if (meta == null || meta.bytes() == null || meta.bytes().length == 0) {
            return 0;
        }
        String format = meta.sourceFormat();
        if (!"xlsx".equals(format) && !"xlsm".equals(format) && !"xls".equals(format)) {
            return 0;
        }
        try {
            return KnowledgeContentConverter.unevaluatedFormulaCount(meta.bytes());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private byte[] readOriginal(AdminAiKnowledgeDocumentEntity entity) {
        if (!StringUtils.hasText(entity.getStorageBucket()) || !StringUtils.hasText(entity.getStorageKey())) {
            return null;
        }
        try {
            return objectStorageService.get(entity.getStorageBucket(), entity.getStorageKey());
        } catch (RuntimeException ex) {
            log.warn(
                    "KB original read failed (agentId={}, documentId={}); indexing without location",
                    entity.getAgentId(),
                    entity.getId(),
                    ex);
            return null;
        }
    }

    /** PDF 新上传把页标题写入正文。扫描件抽不出文字层时保留调用方已经转写的正文。 */
    private static String storedUploadContent(String requested, AdminAiKnowledgeUploadMeta meta) {
        if (meta == null || !"pdf".equals(meta.sourceFormat()) || meta.bytes() == null) {
            return requested;
        }
        try {
            String paged = KnowledgeTextExtractor.pagedMarkdown(meta.bytes());
            return StringUtils.hasText(paged) ? paged : requested;
        } catch (ExtractionException ex) {
            return requested;
        }
    }

    /** 从文档的全部目标分区移除（所有者 + 共享链接站点），逐个 best-effort。 */
    private void removeFromAllPartitions(AdminAiKnowledgeDocumentEntity entity) {
        for (Long target : partitionAgentIds(entity)) {
            removeFromDerivedIndex(target, entity.getId());
        }
    }

    private static String indexError(RuntimeException ex) {
        if (ex instanceof BusinessException business) {
            if (business.getCode() == ErrorCode.RETRIEVAL_NOT_CONFIGURED.getCode()) {
                Object[] args = business.getMessageArgs();
                if (args.length > 0 && args[0] != null) {
                    return String.valueOf(args[0]);
                }
            }
            return business.getMessageKey();
        }
        return ex.getClass().getSimpleName();
    }

    private static String sourceId(long documentId) {
        return "kb:" + documentId;
    }

    private record SelectionGroup(AgentModelSelection selection) {}

    private record Probe(int dimensions, Integer columnDimensions) {}

    private void requireSite(long agentId) {
        if (adminAgentMapper.selectById(agentId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    private AdminAiKnowledgeDocumentEntity requireDocumentInSite(long agentId, long documentId) {
        AdminAiKnowledgeDocumentEntity document = knowledgeMapper.selectById(documentId);
        // 跨站或不存在统一 404，避免泄露归属
        if (document == null || !Long.valueOf(agentId).equals(document.getAgentId())) {
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
            AdminAiKnowledgeDocumentEntity entity, long viewerAgentId, boolean indexStale) {
        return new AdminAiKnowledgeListItemDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getBaseId(),
                Long.valueOf(viewerAgentId).equals(entity.getAgentId()),
                entity.getTitle(),
                entity.getStatus(),
                entity.getIndexStatus(),
                entity.getIndexError(),
                entity.getIndexedAt(),
                entity.getIndexEmbeddingSpec(),
                indexStale,
                entity.getSourceFormat(),
                entity.getOriginalFilename(),
                entity.getFileSizeBytes(),
                StringUtils.hasText(entity.getStorageKey()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                formulaCount(entity));
    }

    private static AdminAiKnowledgeDetailDto toDetail(AdminAiKnowledgeDocumentEntity entity, boolean indexStale) {
        return new AdminAiKnowledgeDetailDto(
                entity.getId(),
                entity.getAgentId(),
                entity.getBaseId(),
                true,
                entity.getTitle(),
                entity.getContent(),
                entity.getStatus(),
                entity.getIndexStatus(),
                entity.getIndexError(),
                entity.getIndexedAt(),
                entity.getIndexEmbeddingSpec(),
                indexStale,
                entity.getSourceFormat(),
                entity.getOriginalFilename(),
                entity.getFileSizeBytes(),
                StringUtils.hasText(entity.getStorageKey()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                formulaCount(entity));
    }

    private static int formulaCount(AdminAiKnowledgeDocumentEntity entity) {
        Integer count = entity.getUnevaluatedFormulaCount();
        return count == null ? 0 : count;
    }
}
