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
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeRebuildReportDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeRebuildResidueDto;
import com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeRebuildSpaceDto;
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
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper;
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
import com.mywebsite.agent.dto.AgentRetrievalPartitionStats;
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
import java.util.Comparator;
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
    private final AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper;
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
            AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper,
            ExecutorService adminAiStreamExecutor,
            AdminAiKnowledgeRetrievalSyncIssueMapper syncIssueMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.agentClient = agentClient;
        this.modelEntryService = modelEntryService;
        this.objectStorageService = objectStorageService;
        this.storageProperties = storageProperties;
        this.baseMapper = baseMapper;
        this.subscriptionMapper = subscriptionMapper;
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
            // 未指定库：本站创建的空间下的文档。文档只属空间（Issue #209 UoW-5 起
            // 文档 agent_id 列已删），所有者经 base 创建者推导；无自有空间即无文档。
            List<Long> ownedBaseIds = createdBaseIds(agentId);
            if (ownedBaseIds.isEmpty()) {
                wrapper.eq(AdminAiKnowledgeDocumentEntity::getId, -1L);
            } else {
                wrapper.in(AdminAiKnowledgeDocumentEntity::getBaseId, ownedBaseIds);
            }
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
        // 文档 agent_id 已停写（Issue #209 UoW-1）：所有者经所属 base 的创建者推导。
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
        // 去重口径（Issue #209 UoW-1）：文档 agent_id 已停写，改按创建者空间集＋sha256 查——
        // 文档行只存在于创建者空间，与旧 agent 全域去重等价；无自有空间时必无重复，跳过查询。
        List<Long> createdBaseIds = createdBaseIds(agentId);
        AdminAiKnowledgeDocumentEntity duplicate = createdBaseIds.isEmpty()
                ? null
                : knowledgeMapper.selectOne(
                        new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                                .in(AdminAiKnowledgeDocumentEntity::getBaseId, createdBaseIds)
                                .eq(AdminAiKnowledgeDocumentEntity::getSha256, meta.sha256()));
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.FILE_DUPLICATE);
        }

        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        // 文档 agent_id 已停写（Issue #209 UoW-1）：所有者经所属 base 的创建者推导。
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
                        || !Objects.equals(ownerAgentId(current), agentId)
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
            bySite.computeIfAbsent(ownerAgentId(doc), key -> new ArrayList<>()).add(doc);
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
        agentStats.sort(Comparator.comparing(
                AdminAiKnowledgeAgentStatDto::agentId,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<AdminAiKnowledgeFailureDto> recentFailures = documents.stream()
                .filter(doc -> AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED.equals(doc.getIndexStatus()))
                .sorted((a, b) -> {
                    OffsetDateTime left = a.getUpdatedAt() == null ? OffsetDateTime.MIN : a.getUpdatedAt();
                    OffsetDateTime right = b.getUpdatedAt() == null ? OffsetDateTime.MIN : b.getUpdatedAt();
                    return right.compareTo(left);
                })
                .limit(10)
                .map(doc -> {
                    Long ownerId = ownerAgentId(doc);
                    return new AdminAiKnowledgeFailureDto(
                            doc.getId(),
                            ownerId,
                            agentNames.get(ownerId),
                            doc.getTitle(),
                            doc.getIndexError(),
                            doc.getUpdatedAt());
                })
                .toList();

        // Issue #203：同步问题表让待删残留在总览可见（此前只存在于日志）。
        // Issue #209 UoW-2 后只剩空间分区的 DELETE tombstone；OP_UPSERT（链接分区
        // 滞后）已退役，其计数字段已随 UoW-5 管理面收口从 DTO 删除。
        List<AdminAiKnowledgeRetrievalSyncIssueEntity> syncIssues = syncIssueMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity>());
        if (syncIssues == null) {
            syncIssues = List.of();
        }
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
                pendingRetrievalDeletes,
                oldestSyncIssueAt);
    }

    /**
     * 存量全量重建＋对账（Issue #209 UoW-5）。顺序不可换：先按旧 agent id 清扫
     * 遗留分区，再按空间重建——agent id 与 base id 是两套独立序列、数值可相撞，
     * 若先重建后清扫，清扫会把 id 相撞的真空间分区一并删掉；先清扫时被误删的
     * 相撞分区则在重建步骤按空间补回。每步失败只影响局部：清扫/统计取数失败
     * 只记日志，单篇同步失败沿现行机制落 FAILED 并计入报告，不中断整体。
     */
    @Override
    public AdminAiKnowledgeRebuildReportDto rebuildAllSpaces() {
        List<Long> agentIds = adminAgentMapper.selectList(new LambdaQueryWrapper<AdminAgentEntity>())
                .stream()
                .map(AdminAgentEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        // 步骤一：旧分区清扫（旧分区键即 agent id，best-effort，失败只记日志）
        for (Long agentId : agentIds) {
            try {
                agentClient.deleteRetrievalDocumentsByBase(String.valueOf(agentId));
            } catch (RuntimeException ex) {
                log.warn("KB rebuild legacy partition sweep failed (agentId={}); continuing", agentId, ex);
            }
        }

        List<AdminAiKnowledgeBaseEntity> bases = baseMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getId));
        Set<Long> baseIds = new LinkedHashSet<>();
        for (AdminAiKnowledgeBaseEntity base : bases) {
            baseIds.add(base.getId());
        }

        // 步骤二：按空间重建——遍历全部空间，对每个空间的全部 ACTIVE 文档走现行单写同步路径
        List<AdminAiKnowledgeRebuildSpaceDto> spaceReports = new ArrayList<>();
        long totalDocuments = 0;
        long totalChunks = 0;
        long totalSpecs = 0;
        long totalSyncFailures = 0;
        for (AdminAiKnowledgeBaseEntity base : bases) {
            List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                    new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                            .eq(AdminAiKnowledgeDocumentEntity::getBaseId, base.getId())
                            .eq(
                                    AdminAiKnowledgeDocumentEntity::getStatus,
                                    AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                            .orderByAsc(AdminAiKnowledgeDocumentEntity::getId));
            long syncFailures = 0;
            long specDocuments = 0;
            for (AdminAiKnowledgeDocumentEntity document : documents) {
                syncDerivedIndex(document);
                if (AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(document.getIndexStatus())) {
                    if (StringUtils.hasText(document.getIndexEmbeddingSpec())) {
                        specDocuments++;
                    }
                } else {
                    syncFailures++;
                }
            }
            // 步骤三（按空间对账）：索引源数/块数取自执行层分区统计，取不到记 -1（未知，非零值）
            long indexedSources = -1;
            long chunks = -1;
            try {
                AgentRetrievalPartitionStats stats =
                        agentClient.retrievalPartitionStats(String.valueOf(base.getId()));
                indexedSources = stats.sourceIds().size();
                chunks = stats.chunkCount();
            } catch (RuntimeException ex) {
                log.warn(
                        "KB rebuild partition stats failed (baseId={}); report carries -1",
                        base.getId(),
                        ex);
            }
            spaceReports.add(new AdminAiKnowledgeRebuildSpaceDto(
                    base.getId(), documents.size(), indexedSources, chunks, syncFailures));
            totalDocuments += documents.size();
            totalChunks += Math.max(chunks, 0);
            totalSpecs += specDocuments;
            totalSyncFailures += syncFailures;
        }

        // 步骤三（旧分区残留检查）：id 不是现存空间的 agent 键下不应再有索引源；
        // 是现存空间的键下有内容属重建成果，不算残留。取数失败只记日志跳过该键。
        List<AdminAiKnowledgeRebuildResidueDto> residues = new ArrayList<>();
        for (Long agentId : agentIds) {
            if (baseIds.contains(agentId)) {
                continue;
            }
            try {
                AgentRetrievalPartitionStats stats =
                        agentClient.retrievalPartitionStats(String.valueOf(agentId));
                if (!stats.sourceIds().isEmpty()) {
                    residues.add(new AdminAiKnowledgeRebuildResidueDto(agentId, stats.sourceIds().size()));
                }
            } catch (RuntimeException ex) {
                log.warn("KB rebuild legacy residue check failed (agentId={}); skipped", agentId, ex);
            }
        }

        return new AdminAiKnowledgeRebuildReportDto(
                List.copyOf(spaceReports),
                totalDocuments,
                totalChunks,
                totalSpecs,
                totalSyncFailures,
                List.copyOf(residues));
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
        if (!Long.valueOf(agentId).equals(ownerAgentId(entity))) {
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
                .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId)
                .orderByAsc(AdminAiKnowledgeBaseEntity::getId)));
        // 订阅表含创建者行（V57 回填）：自建空间已在上一步列入，按 base id 去重避免重复出现
        Set<Long> seenBaseIds = new LinkedHashSet<>();
        for (AdminAiKnowledgeBaseEntity base : visible) {
            seenBaseIds.add(base.getId());
        }
        List<AdminAiKnowledgeBaseSubscriptionEntity> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId));
        for (AdminAiKnowledgeBaseSubscriptionEntity subscription : subscriptions) {
            AdminAiKnowledgeBaseEntity base = baseMapper.selectById(subscription.getBaseId());
            if (base != null && seenBaseIds.add(base.getId())) {
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
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getCreatedByAgentId)
                        .orderByAsc(AdminAiKnowledgeBaseEntity::getId))
                .stream()
                .map(base -> toBaseDto(base, base.getCreatedByAgentId(), agentNames))
                .toList();
    }

    @Override
    @Transactional
    public AdminAiKnowledgeBaseDto createBase(long agentId, AdminAiKnowledgeBaseCreateRequest request) {
        requireSite(agentId);
        AdminAiKnowledgeBaseEntity base = new AdminAiKnowledgeBaseEntity();
        base.setCreatedByAgentId(agentId);
        base.setName(request.name().trim());
        base.setIsDefault(false);
        base.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(base);
        insertCreatorSubscription(base);
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
        // 该库文档为零：清订阅行后整分区 best-effort 清扫一遍（Issue #209 UoW-2），
        // 防逐篇删除失败等路径留下的孤儿块；失败只记日志——分区已无文档行、
        // 且空间删除后不会再被任何 agent 的空间集展开到，残留不可达。
        subscriptionMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, baseId));
        try {
            agentClient.deleteRetrievalDocumentsByBase(String.valueOf(baseId));
        } catch (RuntimeException ex) {
            log.warn("KB base partition sweep failed (baseId={}); orphan chunks may linger", baseId, ex);
        }
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
        if (Long.valueOf(targetAgentId).equals(base.getCreatedByAgentId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAiKnowledgeMessageKeys.BASE_LINK_SELF);
        }
        AdminAiKnowledgeBaseSubscriptionEntity existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, targetAgentId));
        if (existing != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAiKnowledgeMessageKeys.BASE_ALREADY_LINKED);
        }
        AdminAiKnowledgeBaseSubscriptionEntity subscription = new AdminAiKnowledgeBaseSubscriptionEntity();
        subscription.setBaseId(baseId);
        subscription.setAgentId(targetAgentId);
        subscriptionMapper.insert(subscription);
        // Issue #209 UoW-2：向量按空间单写，订阅不再触发索引扇出——目标 agent 的
        // 可检索空间集展开时带上本空间即可命中既有向量，link 只写订阅行。
    }

    @Override
    @Transactional
    public void unlinkBase(long baseId, long targetAgentId) {
        AdminAiKnowledgeBaseSubscriptionEntity existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, targetAgentId));
        if (existing == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        subscriptionMapper.deleteById(existing.getId());
        // Issue #209 UoW-2：解链不删索引——向量只存空间分区一份，其他订阅者仍要
        // 命中；目标 agent 的空间集展开不再包含本空间，自然检索不到。
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
        // 订阅表含创建者行（V57 回填）：linkedAgentIds 沿用旧口径（仅其他订阅者），排除创建者本人
        List<Long> linkedAgentIds = subscriptionMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, base.getId()))
                .stream()
                .map(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId)
                .filter(agentId -> !Objects.equals(agentId, base.getCreatedByAgentId()))
                .toList();
        return new AdminAiKnowledgeBaseDto(
                base.getId(),
                base.getCreatedByAgentId(),
                agentNames.get(base.getCreatedByAgentId()),
                base.getName(),
                Boolean.TRUE.equals(base.getIsDefault()),
                Long.valueOf(viewerAgentId).equals(base.getCreatedByAgentId()),
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
                        .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId)
                        .eq(AdminAiKnowledgeBaseEntity::getIsDefault, true));
        if (defaultBase != null) {
            return defaultBase.getId();
        }
        AdminAiKnowledgeBaseEntity created = new AdminAiKnowledgeBaseEntity();
        created.setCreatedByAgentId(agentId);
        created.setName("默认知识库");
        created.setIsDefault(true);
        created.setStatus(AdminAiKnowledgeBaseEntity.STATUS_ACTIVE);
        baseMapper.insert(created);
        insertCreatorSubscription(created);
        return created.getId();
    }

    private AdminAiKnowledgeBaseEntity requireOwnedBase(long agentId, long baseId) {
        AdminAiKnowledgeBaseEntity base = baseMapper.selectById(baseId);
        if (base == null || !Long.valueOf(agentId).equals(base.getCreatedByAgentId())) {
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
        if (Long.valueOf(agentId).equals(base.getCreatedByAgentId())) {
            return base;
        }
        AdminAiKnowledgeBaseSubscriptionEntity subscription = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, baseId)
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId));
        if (subscription == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
        return base;
    }

    /**
     * 文档的「所有者 agent」推导：文档只属空间（Issue #209 UoW-5 起文档
     * {@code agent_id} 列已删），所有者一律经所属 base 的创建者
     * （createdByAgentId）推导；文档未挂 base 或 base 不存在时为无主（null）——
     * 同步路径对无空间文档已先行按 PENDING 返回，无主文档不索引。
     */
    private Long ownerAgentId(AdminAiKnowledgeDocumentEntity entity) {
        if (entity.getBaseId() != null) {
            AdminAiKnowledgeBaseEntity base = baseMapper.selectById(entity.getBaseId());
            if (base != null) {
                return base.getCreatedByAgentId();
            }
        }
        return null;
    }

    /** 该 agent 创建的空间 id 集：文档行只存在于创建者空间，空间口径查询以此展开。 */
    private List<Long> createdBaseIds(long agentId) {
        return baseMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId))
                .stream()
                .map(AdminAiKnowledgeBaseEntity::getId)
                .toList();
    }

    /** 新建空间同步落创建者订阅行，与 V57 存量回填口径一致（DTO 的 linkedAgentIds 排除创建者，前台无感）。 */
    private void insertCreatorSubscription(AdminAiKnowledgeBaseEntity base) {
        AdminAiKnowledgeBaseSubscriptionEntity subscription = new AdminAiKnowledgeBaseSubscriptionEntity();
        subscription.setBaseId(base.getId());
        subscription.setAgentId(base.getCreatedByAgentId());
        subscriptionMapper.insert(subscription);
    }

    /**
     * 文档所属空间的嵌入类集（Issue #209 UoW-3 起为空间原生登记）：分区键与
     * 登记 scope 同为 base，类集只由空间自己的登记（空间行优先、全站平台默认
     * 补充）决定，不再经创建者推导。无空间归属的文档没有类集——同步路径对无
     * 空间文档已先行按 PENDING 返回，走不到这里，空列表只是防御。
     */
    private List<AgentModelSelection> spaceClasses(AdminAiKnowledgeDocumentEntity entity) {
        if (entity.getBaseId() == null) {
            return List.of();
        }
        return registeredClassesForSpace(entity.getBaseId());
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
        removeFromSpacePartition(entity);
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
                    "KB original file remove failed (baseId={}, documentId={}); object may linger",
                    entity.getBaseId(),
                    entity.getId(),
                    ex);
        }
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        // 文档经 base 创建者推导归属（Issue #209 UoW-1 起、UoW-5 删列定稿）：
        // 先取该 agent 创建的全部空间，再按空间取文档。
        List<AdminAiKnowledgeBaseEntity> bases = baseMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId));
        List<Long> ownedBaseIds = bases.stream().map(AdminAiKnowledgeBaseEntity::getId).toList();
        List<AdminAiKnowledgeDocumentEntity> documents = ownedBaseIds.isEmpty()
                ? List.of()
                : knowledgeMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .in(AdminAiKnowledgeDocumentEntity::getBaseId, ownedBaseIds));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminAiKnowledgeDocumentEntity document : documents) {
            document.setDeletedAt(now);
            knowledgeMapper.updateById(document);
            knowledgeMapper.deleteById(document.getId());
            removeFromSpacePartition(document);
        }
        // 站点删除时，其名下知识库与订阅行一并清理；每库再整分区清扫一遍兜底
        // （逐篇删除失败的残留 tombstone 重试无文档可依时，分区清扫保证不留孤儿块）
        for (AdminAiKnowledgeBaseEntity base : bases) {
            subscriptionMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                    .eq(AdminAiKnowledgeBaseSubscriptionEntity::getBaseId, base.getId()));
            try {
                agentClient.deleteRetrievalDocumentsByBase(String.valueOf(base.getId()));
            } catch (RuntimeException ex) {
                log.warn(
                        "KB base partition sweep failed (baseId={}); orphan chunks may linger",
                        base.getId(),
                        ex);
            }
            base.setDeletedAt(now);
            baseMapper.updateById(base);
            baseMapper.deleteById(base.getId());
        }
        subscriptionMapper.delete(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId));
    }

    @Override
    public AdminAiKnowledgeReembedResultDto reembedAll() {
        List<AdminAiKnowledgeDocumentEntity> documents = knowledgeMapper.selectList(
                new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getBaseId)
                        .orderByAsc(AdminAiKnowledgeDocumentEntity::getId));
        // 分组键为知识空间（Issue #209 UoW-2）：向量按 base 单写，重嵌按空间逐份进行；
        // 无空间归属的存量文档没有分区可写，跳过（与同步路径的 PENDING 口径一致）。
        Map<Long, List<AdminAiKnowledgeDocumentEntity>> documentsByBase = new LinkedHashMap<>();
        for (AdminAiKnowledgeDocumentEntity document : documents) {
            if (document.getBaseId() == null || document.getId() == null) {
                continue;
            }
            documentsByBase.computeIfAbsent(document.getBaseId(), ignored -> new ArrayList<>()).add(document);
        }
        if (documentsByBase.isEmpty()) {
            return new AdminAiKnowledgeReembedResultDto(0, 0, null, false, List.of(), REEMBED_SUCCESS);
        }

        // 空间 → 创建者映射只供冲突/失败 DTO 的归属标注（对外沿用 agentId 字段）；
        // 类集按空间原生登记逐空间取（Issue #209 UoW-3，见 spaceClasses）。
        Map<Long, Long> creatorByBase = new LinkedHashMap<>();
        for (AdminAiKnowledgeBaseEntity base :
                baseMapper.selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>())) {
            creatorByBase.put(base.getId(), base.getCreatedByAgentId());
        }
        Map<Long, List<AgentModelSelection>> classesByBase = new LinkedHashMap<>();
        boolean multiClass = false;
        for (Long baseId : documentsByBase.keySet()) {
            List<AgentModelSelection> classes = registeredClassesForSpace(baseId);
            classesByBase.put(baseId, classes);
            if (classes.size() > 1) {
                multiClass = true;
            }
        }

        Integer dimensions = null;
        boolean recreatedSchema = false;
        // 多类的表由 upsert 按向量长度另建。confirm 重建只会 DROP 遗留 chunks，同维度的其它类会被清掉。
        // 有空间登记了两类及以上时，整批都不走这条重建。
        if (!multiClass) {
            Map<SelectionGroup, List<Long>> basesBySelection = new LinkedHashMap<>();
            for (Map.Entry<Long, List<AgentModelSelection>> base : classesByBase.entrySet()) {
                AgentModelSelection selection = base.getValue().isEmpty() ? null : base.getValue().get(0);
                basesBySelection
                        .computeIfAbsent(new SelectionGroup(selection), ignored -> new ArrayList<>())
                        .add(base.getKey());
            }
            Map<Long, Integer> dimensionsByBase = new LinkedHashMap<>();
            Integer columnDimensions = null;
            AgentModelSelection confirmSelection = null;
            boolean sawSelection = false;
            for (Map.Entry<SelectionGroup, List<Long>> group : basesBySelection.entrySet()) {
                Probe probe = probeSelection(group.getKey().selection());
                if (!sawSelection) {
                    confirmSelection = group.getKey().selection();
                    sawSelection = true;
                }
                if (probe.columnDimensions() != null) {
                    columnDimensions = probe.columnDimensions();
                }
                for (Long baseId : group.getValue()) {
                    dimensionsByBase.put(baseId, probe.dimensions());
                }
            }
            if (new LinkedHashSet<>(dimensionsByBase.values()).size() > 1) {
                // 冲突以空间创建者标注（对外 DTO 沿用 agentId 字段，值取空间创建者）
                List<AdminAiKnowledgeReembedConflictDto> conflicts = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : dimensionsByBase.entrySet()) {
                    Long creatorId = creatorByBase.get(entry.getKey());
                    conflicts.add(new AdminAiKnowledgeReembedConflictDto(
                            creatorId == null ? entry.getKey() : creatorId, entry.getValue()));
                }
                throw new ReembedConflictException(conflicts);
            }
            dimensions = dimensionsByBase.values().iterator().next();
            if (columnDimensions == null || !columnDimensions.equals(dimensions)) {
                AgentReembedPrepareResult recreated = agentClient.prepareReembed(confirmSelection, true);
                dimensions = recreated.dimensions();
                recreatedSchema = recreated.recreated();
            }
        }

        List<AdminAiKnowledgeReembedFailureDto> failed = new ArrayList<>();
        int attempted = 0;
        for (Map.Entry<Long, List<AdminAiKnowledgeDocumentEntity>> base : documentsByBase.entrySet()) {
            List<AgentModelSelection> classes = classesByBase.get(base.getKey());
            Long creatorId = creatorByBase.get(base.getKey());
            for (AdminAiKnowledgeDocumentEntity document : base.getValue()) {
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
                    // 失败以空间创建者标注（类集归属者；对外 DTO 沿用 agentId 字段，空间未知时以 base id 兜底）
                    failed.add(new AdminAiKnowledgeReembedFailureDto(
                            creatorId == null ? base.getKey() : creatorId,
                            sourceId(document.getId()),
                            indexError(ex)));
                }
            }
        }
        return new AdminAiKnowledgeReembedResultDto(
                documentsByBase.size(),
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
     * 文档只写其所属知识空间一个分区（Issue #209 UoW-2，分区键 base）；落库的
     * index_status 即该空间分区的同步结果，订阅者经查询时展开空间集共享同一份向量。
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
        if (entity.getBaseId() == null) {
            // 无空间归属的存量文档没有分区可写：如实记 PENDING（未在索引中），不假装已同步。
            log.warn(
                    "KB derived index skipped: document has no knowledge base (documentId={})",
                    entity.getId());
            recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING, null);
            return;
        }
        DerivedIndexParts parts = derivedIndexParts(entity, originalBytes);
        if (AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            List<AgentModelSelection> spaceClasses = spaceClasses(entity);
            List<AgentModelSelection> toWrite = classesToWrite(entity, spaceClasses, allowFillMissing);
            // Issue #202 UoW-2：只有 toWrite 就是完整登记类集时，Python 才可按差集清理
            // 已注销类的残留行；fill-missing 子集补写（真子集）时 complete=false，不清理。
            boolean classesComplete = Objects.equals(
                    AgentEmbeddingClassUtils.canonical(toWrite),
                    AgentEmbeddingClassUtils.canonical(spaceClasses));
            try {
                upsertSpaceIndex(entity, toWrite, parts, classesComplete);
                recordIndexOutcome(
                        entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED, null, spaceClasses);
            } catch (RuntimeException ex) {
                log.warn(
                        "KB derived index sync failed (baseId={}, documentId={}); index may lag",
                        entity.getBaseId(),
                        entity.getId(),
                        ex);
                recordIndexOutcome(
                        entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED, indexError(ex));
            }
        } else {
            try {
                deleteFromSpacePartition(entity);
            } catch (RuntimeException ex) {
                log.warn(
                        "KB derived index delete failed (baseId={}, documentId={}); index may lag",
                        entity.getBaseId(),
                        entity.getId(),
                        ex);
            }
            recordIndexOutcome(entity, AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING, null);
        }
    }

    /** 按空间单写派生索引（完整/子集类集由 classesComplete 标注，语义见 AgentRetrievalDocument）。 */
    private void upsertSpaceIndex(
            AdminAiKnowledgeDocumentEntity entity,
            List<AgentModelSelection> classes,
            DerivedIndexParts parts,
            boolean classesComplete) {
        agentClient.upsertRetrievalDocument(retrievalDocument(entity, classes, parts, classesComplete));
        // Issue #203 复评三审计：写入成功即作废同空间同文档的待删 tombstone——
        // 否则文档停用期落下的 DELETE 行会在文档恢复 ACTIVE 并重写后被重试任务
        // 执行，把活向量删掉（文档 INDEXED 却检索不到）。
        clearSyncIssue(
                entity.getBaseId(), sourceId(entity.getId()), AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE);
    }

    private static AgentRetrievalDocument retrievalDocument(
            AdminAiKnowledgeDocumentEntity entity,
            List<AgentModelSelection> classes,
            DerivedIndexParts parts,
            boolean classesComplete) {
        List<AgentModelSelection> registered = classes == null ? List.of() : classes;
        AgentModelSelection first = registered.isEmpty() ? null : registered.get(0);
        List<AgentRetrievalSegment> segments = parts == null ? null : parts.segments();
        List<AgentRetrievalTable> tables = parts == null ? null : parts.tables();
        return new AgentRetrievalDocument(
                String.valueOf(entity.getBaseId()),
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

    private List<AgentModelSelection> registeredClassesForSpace(long baseId) {
        List<AgentModelSelection> classes = modelEntryService.embeddingClassesForSpace(baseId);
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
     * 并把 indexEmbeddingSpec 落为当次空间已登记的嵌入类名（没有登记时写 NULL）；
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
     * 文档 {@code indexStatus == INDEXED} 且记下的类名与当前空间类集不一致时为 stale。
     * 新登记一类也算。非 INDEXED 文档恒为 false。判定按（空间,文档）键（Issue #209
     * UoW-2）：类集取文档所属空间的当前类集；缓存按 baseId 键（同一空间的文档共用
     * 一份），无空间的存量行统一以 0 作键、以空集比对。供列表和总览复用。
     */
    private boolean isIndexStale(
            AdminAiKnowledgeDocumentEntity entity, Map<Long, List<AgentModelSelection>> selectionCache) {
        if (!AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED.equals(entity.getIndexStatus())) {
            return false;
        }
        long cacheKey = entity.getBaseId() == null ? 0L : entity.getBaseId();
        // 类集按空间原生登记取（Issue #209 UoW-3）；无空间的存量行没有当前
        // 类集可比，以空集比对：记有类名即 stale、未记则不 stale（与
        // matchesStored 对空集的口径自洽）。
        List<AgentModelSelection> current = selectionCache.computeIfAbsent(
                cacheKey,
                key -> entity.getBaseId() == null
                        ? List.of()
                        : registeredClassesForSpace(entity.getBaseId()));
        return !AgentEmbeddingClassUtils.matchesStored(entity.getIndexEmbeddingSpec(), current);
    }

    /** 从文档所属空间分区移除（best-effort）：失败已在删除路径内落 tombstone 行，这里只记日志。 */
    private void removeFromSpacePartition(AdminAiKnowledgeDocumentEntity entity) {
        if (entity.getBaseId() == null) {
            return;
        }
        try {
            deleteFromSpacePartition(entity);
        } catch (RuntimeException ex) {
            log.warn(
                    "KB derived index delete failed (baseId={}, documentId={}); index may lag",
                    entity.getBaseId(),
                    entity.getId(),
                    ex);
        }
    }

    // ------------------------------------------------------------------
    // Issue #203：派生索引同步问题（落行＋定时重试）；Issue #209 UoW-2 换键为
    // (base_id, source_id, op)，只剩空间分区的 DELETE tombstone（agent_id 留痕）
    // ------------------------------------------------------------------

    private static LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity> syncIssueKey(
            Long baseId, String sourceId, String op) {
        return new LambdaQueryWrapper<AdminAiKnowledgeRetrievalSyncIssueEntity>()
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getBaseId, baseId)
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getSourceId, sourceId)
                .eq(AdminAiKnowledgeRetrievalSyncIssueEntity::getOp, op);
    }

    /** 落/更新同步问题行（键为空间；agentId 只写空间创建者作留痕）。落行本身失败只记日志，不能掩盖原始同步结果。 */
    private void recordSyncIssue(
            Long baseId, Long agentId, String sourceId, String op, RuntimeException failure) {
        try {
            AdminAiKnowledgeRetrievalSyncIssueEntity existing =
                    syncIssueMapper.selectOne(syncIssueKey(baseId, sourceId, op));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (existing == null) {
                AdminAiKnowledgeRetrievalSyncIssueEntity issue =
                        new AdminAiKnowledgeRetrievalSyncIssueEntity();
                issue.setBaseId(baseId);
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
                    "KB retrieval sync issue record failed (baseId={}, sourceId={}, op={})",
                    baseId,
                    sourceId,
                    op,
                    recordFailure);
        }
    }

    /** 同步成功后清掉对应的问题行；清行失败只记日志。 */
    private void clearSyncIssue(Long baseId, String sourceId, String op) {
        try {
            syncIssueMapper.delete(syncIssueKey(baseId, sourceId, op));
        } catch (RuntimeException clearFailure) {
            log.warn(
                    "KB retrieval sync issue clear failed (baseId={}, sourceId={}, op={})",
                    baseId,
                    sourceId,
                    op,
                    clearFailure);
        }
    }

    /** 空间分区删除：成功清 op=DELETE 与 op=UPSERT 问题行（删除取代待同步），失败落 tombstone 行并原样抛回。 */
    private void deleteFromSpacePartition(AdminAiKnowledgeDocumentEntity entity) {
        Long baseId = entity.getBaseId();
        String sourceId = sourceId(entity.getId());
        try {
            agentClient.deleteRetrievalDocument(String.valueOf(baseId), sourceId);
            clearSyncIssue(baseId, sourceId, AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE);
            clearSyncIssue(baseId, sourceId, AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT);
        } catch (RuntimeException ex) {
            recordSyncIssue(
                    baseId,
                    ownerAgentId(entity),
                    sourceId,
                    AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE,
                    ex);
            throw ex;
        }
    }

    /**
     * 定时重试同步问题行（Issue #203；键为空间，Issue #209 UoW-2）：DELETE 按
     * (base, source) 重删。已被后续重写取代的 tombstone（文档恢复 ACTIVE 且仍属
     * 该空间）直接作废删行；op=UPSERT 是已退役的链接分区记账（V58 存量全删、
     * 新代码不再产生），偶见行直接作废。重试再失败只累加 attempts，留下轮。
     * 单行失败不影响其它行。
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
                    // 复评三审计第二层防护：落行后文档又恢复 ACTIVE 且仍属该空间时，
                    // 这条 tombstone 已被后续重写取代，作废不执行（与 upsert 成功清行互为冗余）。
                    if (!deleteIssueSuperseded(issue)) {
                        agentClient.deleteRetrievalDocument(
                                String.valueOf(issue.getBaseId()), issue.getSourceId());
                    }
                    syncIssueMapper.deleteById(issue.getId());
                } else if (AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT.equals(issue.getOp())) {
                    // 链接分区记账已退役：空间单写下没有待扇出的 upsert，行直接作废。
                    syncIssueMapper.deleteById(issue.getId());
                } else {
                    // 未知 op：留行待人工处理，只累加次数以便在总览/表里可见异常
                    bumpSyncIssue(issue, null);
                }
            } catch (RuntimeException ex) {
                bumpSyncIssue(issue, ex);
            }
        }
    }

    /** tombstone 是否已被后续重写取代：文档存在、ACTIVE 且仍属该空间（其活向量正应存在于该分区）。 */
    private boolean deleteIssueSuperseded(AdminAiKnowledgeRetrievalSyncIssueEntity issue) {
        Long documentId = parseSourceDocumentId(issue.getSourceId());
        if (documentId == null || issue.getBaseId() == null) {
            return false;
        }
        AdminAiKnowledgeDocumentEntity document = knowledgeMapper.selectById(documentId);
        return document != null
                && AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE.equals(document.getStatus())
                && issue.getBaseId().equals(document.getBaseId());
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
        // reembedAll 传入的是该空间完整登记类集，complete=true。目标分区为文档所属空间。
        upsertSpaceIndex(entity, classes, derivedIndexParts(entity, null), true);
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
                    "KB location segments skipped (baseId={}, documentId={})",
                    entity.getBaseId(),
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
                    "KB original read failed (baseId={}, documentId={}); indexing without location",
                    entity.getBaseId(),
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
        // 跨站或不存在统一 404，避免泄露归属；归属经 base 创建者推导（文档 agent_id 已停写）
        if (document == null || !Long.valueOf(agentId).equals(ownerAgentId(document))) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return document;
    }

    private static void requireStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private AdminAiKnowledgeListItemDto toListItem(
            AdminAiKnowledgeDocumentEntity entity, long viewerAgentId, boolean indexStale) {
        // DTO 的 agentId/owned 沿用所有者口径：所有者经 base 创建者推导（文档 agent_id 已停写）
        Long ownerId = ownerAgentId(entity);
        return new AdminAiKnowledgeListItemDto(
                entity.getId(),
                ownerId,
                entity.getBaseId(),
                Long.valueOf(viewerAgentId).equals(ownerId),
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

    private AdminAiKnowledgeDetailDto toDetail(AdminAiKnowledgeDocumentEntity entity, boolean indexStale) {
        return new AdminAiKnowledgeDetailDto(
                entity.getId(),
                ownerAgentId(entity),
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
