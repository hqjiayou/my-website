package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentDimensionChangeRequiredException;
import com.mywebsite.agent.dto.AgentEmbeddingClass;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentRetrievalPartitionStats;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAiKnowledgeServiceImplTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AdminAiKnowledgeDocumentEntity.class);
    }

    @Mock
    private AdminAiKnowledgeDocumentMapper knowledgeMapper;

    @Mock
    private AdminAgentMapper adminAgentMapper;

    @Mock
    private AgentClient agentClient;

    @Mock
    private AdminAiModelEntryService modelEntryService;

    @Mock
    private com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper baseMapper;

    @Mock
    private com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper;

    @Mock
    private com.mywebsite.admin.mapper.AdminAiKnowledgeRetrievalSyncIssueMapper syncIssueMapper;

    @Mock
    private com.mywebsite.infra.service.ObjectStorageService objectStorageService;

    @Mock
    private ExecutorService adminAiStreamExecutor;

    private AdminAiKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiKnowledgeServiceImpl(
                knowledgeMapper,
                adminAgentMapper,
                agentClient,
                modelEntryService,
                objectStorageService,
                new com.mywebsite.infra.properties.StorageProperties(),
                baseMapper,
                subscriptionMapper,
                adminAiStreamExecutor,
                syncIssueMapper);
    }

    @Test
    void reembedCountsSitesAndActiveDocumentsWhenDimensionsMatch() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(1L, 12L, AdminAiKnowledgeDocumentEntity.STATUS_DISABLED),
                document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(2L, 22L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        // 文档按空间分组（helper 口径：agent 1→空间 901、agent 2→空间 902），类集按空间原生登记取（stub 键为空间 id）
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L), base(902L, 2L)));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection));
        when(modelEntryService.embeddingClassesForSpace(902L)).thenReturn(List.of(selection));
        when(agentClient.prepareReembed(selection, false))
                .thenReturn(new AgentReembedPrepareResult(3, 3, false));

        var result = service.reembedAll();

        assertThat(result.sites()).isEqualTo(2);
        assertThat(result.documents()).isEqualTo(3);
        assertThat(result.dimensions()).isEqualTo(3);
        assertThat(result.recreatedSchema()).isFalse();
        assertThat(result.failed()).isEmpty();
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(agentClient).prepareReembed(selection, false);
        verify(agentClient, never()).prepareReembed(any(), eq(true));
        verify(agentClient, times(3)).upsertRetrievalDocument(any());
    }

    @Test
    void reembedRejectsConflictingDimensionsWithoutUpsert() {
        AgentModelSelection first = selection("openai:embed-a");
        AgentModelSelection second = selection("openai:embed-b");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L), base(902L, 2L)));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(first));
        when(modelEntryService.embeddingClassesForSpace(902L)).thenReturn(List.of(second));
        when(agentClient.prepareReembed(first, false)).thenReturn(new AgentReembedPrepareResult(3, 3, false));
        when(agentClient.prepareReembed(second, false))
                .thenThrow(new AgentDimensionChangeRequiredException(4, 3));

        assertThatThrownBy(() -> service.reembedAll())
                .isInstanceOf(ReembedConflictException.class)
                .satisfies(ex -> assertThat(((ReembedConflictException) ex).conflicts())
                        .extracting(conflict -> conflict.agentId() + ":" + conflict.dimensions())
                        .containsExactly("1:3", "2:4"));
        verify(agentClient, never()).prepareReembed(any(), eq(true));
        verify(agentClient, never()).upsertRetrievalDocument(any());
    }

    @Test
    void reembedConfirmsRecreateBeforeUpsertWhenColumnDimensionDiffers() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any()))
                .thenReturn(List.of(document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection));
        when(agentClient.prepareReembed(selection, false))
                .thenThrow(new AgentDimensionChangeRequiredException(8, 3));
        when(agentClient.prepareReembed(selection, true))
                .thenReturn(new AgentReembedPrepareResult(8, 3, true));

        var result = service.reembedAll();

        assertThat(result.recreatedSchema()).isTrue();
        assertThat(result.dimensions()).isEqualTo(8);
        assertThat(result.status()).isEqualTo("SUCCESS");
        InOrder order = inOrder(agentClient);
        order.verify(agentClient).prepareReembed(selection, false);
        order.verify(agentClient).prepareReembed(selection, true);
        order.verify(agentClient).upsertRetrievalDocument(any(AgentRetrievalDocument.class));
    }

    @Test
    void reembedReturnsPartialWhenOneDocumentFails() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(1L, 22L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection));
        when(agentClient.prepareReembed(selection, false))
                .thenReturn(new AgentReembedPrepareResult(3, 3, false));
        org.mockito.Mockito.doAnswer(invocation -> {
                    AgentRetrievalDocument document = invocation.getArgument(0);
                    if ("kb:22".equals(document.sourceId())) {
                        throw new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
                    }
                    return null;
                })
                .when(agentClient)
                .upsertRetrievalDocument(any());

        var result = service.reembedAll();

        assertThat(result.sites()).isEqualTo(1);
        assertThat(result.documents()).isEqualTo(2);
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().agentId()).isEqualTo(1L);
        assertThat(result.failed().getFirst().sourceId()).isEqualTo("kb:22");
        assertThat(result.failed().getFirst().error())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getMessageKey());
    }

    @Test
    void reembedRecordsMissingRetrievalKeysInsteadOfGenericDependencyError() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any()))
                .thenReturn(List.of(document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection));
        when(agentClient.prepareReembed(selection, false))
                .thenReturn(new AgentReembedPrepareResult(3, 3, false));
        org.mockito.Mockito.doThrow(new BusinessException(
                        ErrorCode.RETRIEVAL_NOT_CONFIGURED,
                        "Retrieval is not configured (missing RETRIEVAL_DATABASE_URL)."))
                .when(agentClient)
                .upsertRetrievalDocument(any());

        var result = service.reembedAll();

        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().error()).contains("RETRIEVAL_DATABASE_URL");
        assertThat(result.failed().getFirst().error())
                .isNotEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getMessageKey());
    }

    private static AgentModelSelection selection(String embeddingSpec) {
        return new AgentModelSelection("openai:gpt", "key", null, embeddingSpec);
    }

    @Test
    void overviewAggregatesAcrossSites() {
        AdminAiKnowledgeDocumentEntity indexedDoc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        indexedDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        indexedDoc.setFileSizeBytes(100L);
        AdminAiKnowledgeDocumentEntity failedDoc = document(1L, 12L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        failedDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED);
        failedDoc.setIndexError("boom");
        failedDoc.setFileSizeBytes(50L);
        AdminAiKnowledgeDocumentEntity pendingDoc = document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        pendingDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(indexedDoc, failedDoc, pendingDoc));
        com.mywebsite.admin.entity.AdminAgentEntity site1 = new com.mywebsite.admin.entity.AdminAgentEntity();
        site1.setId(1L);
        site1.setAgentName("Agent One");
        com.mywebsite.admin.entity.AdminAgentEntity site2 = new com.mywebsite.admin.entity.AdminAgentEntity();
        site2.setId(2L);
        site2.setAgentName("Agent Two");
        when(adminAgentMapper.selectList(any())).thenReturn(List.of(site1, site2));
        // 所有者只经空间创建者推导（Issue #209 UoW-5）：文档 11/12 属空间 901（创建者 1）、21 属 902（创建者 2）
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(baseMapper.selectById(902L)).thenReturn(base(902L, 2L));

        var overview = service.knowledgeOverview();

        assertThat(overview.totalDocuments()).isEqualTo(3);
        assertThat(overview.indexedDocuments()).isEqualTo(1);
        assertThat(overview.failedDocuments()).isEqualTo(1);
        assertThat(overview.pendingDocuments()).isEqualTo(1);
        assertThat(overview.originalBytes()).isEqualTo(150L);
        assertThat(overview.sites()).hasSize(2);
        assertThat(overview.sites().get(0).agentName()).isEqualTo("Agent One");
        assertThat(overview.sites().get(0).failedDocuments()).isEqualTo(1);
        assertThat(overview.recentFailures()).hasSize(1);
        assertThat(overview.recentFailures().get(0).indexError()).isEqualTo("boom");
    }

    // ------------------------------------------------------------------
    // Issue #203：同步问题行（tombstone / 链接分区滞后 / 定时重试 / 总览计数）
    // ------------------------------------------------------------------

    private static com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity syncIssue(
            long id, long baseId, long agentId, String sourceId, String op, int attempts) {
        com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity issue =
                new com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity();
        issue.setId(id);
        // 键为 (baseId, sourceId, op)（Issue #209 UoW-2）；agentId 只作创建者留痕
        issue.setBaseId(baseId);
        issue.setAgentId(agentId);
        issue.setSourceId(sourceId);
        issue.setOp(op);
        issue.setAttempts(attempts);
        return issue;
    }

    @Test
    void linkBaseOnlyWritesSubscriptionRowWithoutTouchingIndex() {
        // Issue #209 UoW-2：向量按空间单写，订阅不再扇出——link 只落订阅行，
        // 不写目标分区、不落同步问题行（目标 agent 靠空间集展开命中既有向量）。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(5L);
        base.setCreatedByAgentId(1L);
        when(baseMapper.selectById(5L)).thenReturn(base);
        com.mywebsite.admin.entity.AdminAgentEntity targetSite = new com.mywebsite.admin.entity.AdminAgentEntity();
        targetSite.setId(2L);
        when(adminAgentMapper.selectById(2L)).thenReturn(targetSite);
        when(subscriptionMapper.selectOne(any())).thenReturn(null);

        service.linkBase(5L, 2L);

        verify(subscriptionMapper).insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity.class));
        verify(agentClient, never()).upsertRetrievalDocument(any());
        verify(syncIssueMapper, never())
                .insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.class));
    }

    @Test
    void unlinkBaseOnlyDeletesSubscriptionRowWithoutTouchingIndex() {
        // Issue #209 UoW-2：解链不删索引——向量只存空间一份，其他订阅者仍要命中；
        // 目标 agent 的空间集展开不再包含本空间即可。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity link =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity();
        link.setId(7L);
        when(subscriptionMapper.selectOne(any())).thenReturn(link);

        service.unlinkBase(5L, 2L);

        verify(subscriptionMapper).deleteById(7L);
        verify(agentClient, never()).deleteRetrievalDocument(any(), any());
        verify(syncIssueMapper, never())
                .insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.class));
    }

    @Test
    void unlinkBaseRejectsRemovingCreatorSubscription() {
        // 解耦深审：创建者订阅行是空间归属锚点，服务端须拒绝移除（前台只是禁用）；
        // 删掉后 linkBase 拒自链、无 API 可补回。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity link =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity();
        link.setId(7L);
        when(subscriptionMapper.selectOne(any())).thenReturn(link);
        when(baseMapper.selectById(5L)).thenReturn(base(5L, 2L));

        assertThatThrownBy(() -> service.unlinkBase(5L, 2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(
                                            com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys
                                                    .BASE_UNLINK_CREATOR);
                        });
        verify(subscriptionMapper, never()).deleteById(any());
    }

    @Test
    void getDocumentAllowsSubscriberOfTheSpace() {
        // 解耦深审：详情读口径与 originalFileUrl 对齐——空间订阅者可读详情。
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(2L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity link =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity();
        link.setId(7L);
        when(subscriptionMapper.selectOne(any())).thenReturn(link);

        var detail = service.getDocument(2L, 11L);

        assertThat(detail.id()).isEqualTo(11L);
        assertThat(detail.title()).isEqualTo("title-11");
        assertThat(detail.agentId()).isEqualTo(1L);
    }

    @Test
    void getDocumentRejectsAgentWithoutSubscription() {
        // 非所有者且未订阅该空间：统一 404，不泄露文档存在性。
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(2L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(subscriptionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getDocument(2L, 11L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void retryDeletesTombstoneAfterSuccessfulDelete() {
        // 键改为空间（Issue #209 UoW-2）：重试删除按 (baseId, sourceId) 执行
        when(syncIssueMapper.selectList(any())).thenReturn(List.of(syncIssue(
                3L,
                5L,
                1L,
                "kb:11",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE,
                1)));

        service.retryRetrievalSyncIssues();

        verify(agentClient).deleteRetrievalDocument("5", "kb:11");
        verify(syncIssueMapper).deleteById(3L);
    }

    @Test
    void retryBumpsAttemptsWhenDeleteStillFails() {
        when(syncIssueMapper.selectList(any())).thenReturn(List.of(syncIssue(
                3L,
                5L,
                1L,
                "kb:11",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE,
                1)));
        doThrow(new RuntimeException("still down"))
                .when(agentClient)
                .deleteRetrievalDocument(any(), any());

        service.retryRetrievalSyncIssues();

        verify(syncIssueMapper).updateById(argThat((com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity issue) -> issue.getAttempts() == 2));
        verify(syncIssueMapper, never()).deleteById(3L);
    }

    @Test
    void retryDropsRetiredUpsertIssueRows() {
        // op=UPSERT 是已退役的链接分区记账（V58 存量全删）：偶见行直接作废，不再重同步
        when(syncIssueMapper.selectList(any())).thenReturn(List.of(syncIssue(
                4L,
                5L,
                1L,
                "kb:99",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT,
                0)));
        service.retryRetrievalSyncIssues();

        verify(syncIssueMapper).deleteById(4L);
        verify(agentClient, never()).upsertRetrievalDocument(any());
    }

    @Test
    void retryDropsTombstoneSupersededByActiveDocument() {
        // 复评三审计：停用期落下的 tombstone，文档恢复 ACTIVE 并重写后不得再被执行；
        // 取代判定改为（Issue #209 UoW-2）：文档 ACTIVE 且仍属 tombstone 的空间。
        when(syncIssueMapper.selectList(any())).thenReturn(List.of(syncIssue(
                3L,
                5L,
                1L,
                "kb:11",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE,
                1)));
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setBaseId(5L);
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);

        service.retryRetrievalSyncIssues();

        verify(syncIssueMapper).deleteById(3L);
        verify(agentClient, never()).deleteRetrievalDocument(any(), any());
    }

    @Test
    void documentDeleteFailureRecordsTombstoneKeyedByBase() {
        // 删文档时空间分区删除失败：落 (baseId, sourceId, DELETE) tombstone 行待重试，
        // 行上 agentId 只作空间创建者留痕（Issue #209 UoW-2）。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(5L);
        base.setCreatedByAgentId(1L);
        when(baseMapper.selectById(5L)).thenReturn(base);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setBaseId(5L);
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(knowledgeMapper.deleteById(11L)).thenReturn(1);
        doThrow(new RuntimeException("python down"))
                .when(agentClient)
                .deleteRetrievalDocument(eq("5"), eq("kb:11"));
        when(syncIssueMapper.selectOne(any())).thenReturn(null);

        service.deleteDocument(1L, 11L);

        verify(syncIssueMapper).insert(argThat((com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity issue) ->
                issue.getBaseId() == 5L
                        && issue.getAgentId() == 1L
                        && "kb:11".equals(issue.getSourceId())
                        && com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE
                                .equals(issue.getOp())));
    }

    @Test
    void documentUpsertSuccessClearsDeleteTombstoneForSameBase() {
        // 漏斗双清按新键等价迁移：upsert 成功清同 (base, source) 的 DELETE 行
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));

        service.reindexDocument(1L, 11L);

        // LambdaQueryWrapper 的参数要在 lambda 列缓存就绪并构建 SQL 段后才物化；
        // 纯 mock 单测无 MyBatis 运行时，先为问题行实体初始化 TableInfo 缓存再断言参数。
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.class);
        verify(syncIssueMapper).delete(argThat((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity>
                wrapper) -> {
                    wrapper.getSqlSegment();
                    return wrapper.getParamNameValuePairs().containsValue(901L)
                            && wrapper.getParamNameValuePairs().containsValue("kb:11")
                            && wrapper.getParamNameValuePairs().containsValue(
                                    com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE);
                }));
    }

    @Test
    void overviewCountsSyncIssues() {
        when(knowledgeMapper.selectList(any())).thenReturn(List.of());
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        var upsert = syncIssue(
                1L,
                5L,
                1L,
                "kb:11",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_UPSERT,
                0);
        upsert.setCreatedAt(java.time.OffsetDateTime.parse("2026-11-05T00:00:00Z"));
        var delete = syncIssue(
                2L,
                5L,
                1L,
                "kb:12",
                com.mywebsite.admin.entity.AdminAiKnowledgeRetrievalSyncIssueEntity.OP_DELETE,
                3);
        delete.setCreatedAt(java.time.OffsetDateTime.parse("2026-11-05T01:00:00Z"));
        when(syncIssueMapper.selectList(any())).thenReturn(List.of(upsert, delete));

        var overview = service.knowledgeOverview();

        assertThat(overview.pendingRetrievalDeletes()).isEqualTo(1);
        assertThat(overview.oldestSyncIssueAt())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-11-05T00:00:00Z"));
    }

    @Test
    void reindexFailedRetriesAndCountsSuccesses() {
        AdminAiKnowledgeDocumentEntity failed = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        failed.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED);
        AdminAiKnowledgeDocumentEntity refreshed = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        refreshed.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(failed));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));
        when(knowledgeMapper.selectById(11L)).thenReturn(refreshed);
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        var result = service.reindexFailedDocuments();

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        verify(agentClient).upsertRetrievalDocument(any());
    }

    @Test
    void createDocumentSyncsOnceToItsBasePartitionEvenWhenSubscribed() {
        // Issue #209 UoW-2 核心验收：同一文档被 2 个 agent 订阅时向量仅写 1 份——
        // 建档只向其所属空间分区写一次，订阅关系不产生额外副本。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity defaultBase =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        defaultBase.setId(9L);
        defaultBase.setCreatedByAgentId(1L);
        defaultBase.setIsDefault(true);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectOne(any())).thenReturn(defaultBase);
        when(baseMapper.selectById(9L)).thenReturn(defaultBase);
        when(knowledgeMapper.insert(any(AdminAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiKnowledgeDocumentEntity inserted = invocation.getArgument(0);
                    inserted.setId(11L);
                    return 1;
                });
        AdminAiKnowledgeDocumentEntity saved = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        saved.setBaseId(9L);
        when(knowledgeMapper.selectById(any())).thenReturn(saved);
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity link =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity();
        link.setBaseId(9L);
        link.setAgentId(3L);
        // 订阅行存在只为证明「有订阅也不多写」：同步路径已不读订阅表，故用 lenient 打桩
        org.mockito.Mockito.lenient()
                .when(subscriptionMapper.selectList(any()))
                .thenReturn(List.of(link));
        when(modelEntryService.embeddingClassesForSpace(9L)).thenReturn(List.of(selection("openai:embed-a")));
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        service.createDocument(
                1L,
                new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest("t", "c"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient, times(1)).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().baseId()).isEqualTo("9");
        assertThat(captor.getValue().sourceId()).isEqualTo("kb:11");
    }

    @Test
    void createDocumentWritesOnlyBaseAndDerivesOwnerFromBaseCreator() {
        // Issue #209 UoW-1 起建档只写 base（文档 agent_id 已随 UoW-5 删列）；所有者经 base 创建者推导（DTO/分区口径不变）
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity defaultBase =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        defaultBase.setId(9L);
        defaultBase.setCreatedByAgentId(1L);
        defaultBase.setIsDefault(true);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectOne(any())).thenReturn(defaultBase);
        when(baseMapper.selectById(9L)).thenReturn(defaultBase);
        when(knowledgeMapper.insert(any(AdminAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiKnowledgeDocumentEntity inserted = invocation.getArgument(0);
                    inserted.setId(11L);
                    return 1;
                });
        AdminAiKnowledgeDocumentEntity saved = new AdminAiKnowledgeDocumentEntity();
        saved.setId(11L);
        saved.setBaseId(9L);
        saved.setTitle("t");
        saved.setContent("c");
        saved.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(knowledgeMapper.selectById(any())).thenReturn(saved);
        when(modelEntryService.embeddingClassesForSpace(9L)).thenReturn(List.of(selection("openai:embed-a")));
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        var detail = service.createDocument(
                1L,
                new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest("t", "c"));

        ArgumentCaptor<AdminAiKnowledgeDocumentEntity> insertedCaptor =
                ArgumentCaptor.forClass(AdminAiKnowledgeDocumentEntity.class);
        verify(knowledgeMapper).insert(insertedCaptor.capture());
        assertThat(insertedCaptor.getValue().getBaseId()).isEqualTo(9L);
        assertThat(detail.agentId()).isEqualTo(1L);
        assertThat(detail.owned()).isTrue();
        var captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        // 分区键已改 base（Issue #209 UoW-2）：载荷首字段是文档所属空间 id
        assertThat(captor.getValue().baseId()).isEqualTo("9");
    }

    @Test
    void ownerIsDerivedFromBaseCreator() {
        // 所有者只经 base 创建者推导（文档 agent_id 已随 Issue #209 UoW-5 删列，无其他来源）
        AdminAiKnowledgeDocumentEntity doc = document(999L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setBaseId(5L);
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(5L);
        base.setCreatedByAgentId(1L);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectById(5L)).thenReturn(base);
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(modelEntryService.embeddingClassesForSpace(5L)).thenReturn(List.of(selection("openai:embed-a")));

        var detail = service.reindexDocument(1L, 11L);

        assertThat(detail.agentId()).isEqualTo(1L);
        var captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        // 分区键已改 base（Issue #209 UoW-2）：写入文档所属空间 5 的分区
        assertThat(captor.getValue().baseId()).isEqualTo("5");
    }

    private static com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base(long id, long creatorId) {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(id);
        base.setCreatedByAgentId(creatorId);
        return base;
    }

    private static com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity subscription(
            long baseId, long agentId) {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity subscription =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity();
        subscription.setBaseId(baseId);
        subscription.setAgentId(agentId);
        return subscription;
    }

    @Test
    void baseDtoLinkedAgentIdsExcludeCreatorSubscriptionRow() {
        // V57 回填后订阅表含创建者行：DTO 的 linkedAgentIds 沿用旧口径（仅其他订阅者），须排除创建者本人
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(5L);
        base.setCreatedByAgentId(1L);
        base.setName("kb");
        when(baseMapper.selectList(any())).thenReturn(List.of(base));
        when(knowledgeMapper.selectCount(any())).thenReturn(0L);
        when(subscriptionMapper.selectList(any()))
                .thenReturn(List.of(subscription(5L, 1L), subscription(5L, 3L)));
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());

        var dtos = service.listAllBases();

        assertThat(dtos).hasSize(1);
        assertThat(dtos.getFirst().agentId()).isEqualTo(1L);
        assertThat(dtos.getFirst().linkedAgentIds()).containsExactly(3L);
    }

    @Test
    void createUploadedDocumentRejectsDuplicateShaWithinCreatorSpaces() {
        // Issue #209 UoW-1 补修：文档不带 agent 归属后，上传去重改按创建者空间集＋sha256——
        // 既有文档只凭 base 归属也须命中同 sha 并抛 CONFLICT，不得再按 document.agentId 查。
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(9L);
        base.setCreatedByAgentId(1L);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectList(any())).thenReturn(List.of(base));
        AdminAiKnowledgeDocumentEntity existing = new AdminAiKnowledgeDocumentEntity();
        existing.setId(5L);
        existing.setBaseId(9L);
        existing.setSha256("sha-dup");
        when(knowledgeMapper.selectOne(any())).thenReturn(existing);
        var meta = new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta(
                "a.md", "md", "text/markdown", 3L, "sha-dup", "abc".getBytes(StandardCharsets.UTF_8));
        var request = new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest(
                "t", "abc", null);

        assertThatThrownBy(() -> service.createUploadedDocument(1L, request, meta))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
        verify(knowledgeMapper, never()).insert(any(AdminAiKnowledgeDocumentEntity.class));
    }

    @Test
    void createUploadedDocumentSkipsDuplicateQueryWhenAgentCreatesNoBase() {
        // 无自有空间时必无重复：跳过文档去重查询，未指定库时懒创建默认库并同步落创建者订阅行
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectOne(any())).thenReturn(null);
        when(baseMapper.insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity.class)))
                .thenAnswer(invocation -> {
                    com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity inserted = invocation.getArgument(0);
                    inserted.setId(9L);
                    return 1;
                });
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity createdBase =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        createdBase.setId(9L);
        createdBase.setCreatedByAgentId(1L);
        createdBase.setIsDefault(true);
        when(baseMapper.selectById(9L)).thenReturn(createdBase);
        when(knowledgeMapper.insert(any(AdminAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiKnowledgeDocumentEntity inserted = invocation.getArgument(0);
                    inserted.setId(77L);
                    return 1;
                });
        AdminAiKnowledgeDocumentEntity saved = new AdminAiKnowledgeDocumentEntity();
        saved.setId(77L);
        saved.setBaseId(9L);
        saved.setTitle("t");
        saved.setContent("abc");
        saved.setStatus(AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        saved.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        when(knowledgeMapper.selectById(77L)).thenReturn(saved);
        var meta = new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUploadMeta(
                "a.md", "md", "text/markdown", 3L, "sha-new", "abc".getBytes(StandardCharsets.UTF_8));
        var request = new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest(
                "t", "abc", null);

        var detail = service.createUploadedDocument(1L, request, meta);

        assertThat(detail.id()).isEqualTo(77L);
        verify(knowledgeMapper, never()).selectOne(any());
        ArgumentCaptor<com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity> captor =
                ArgumentCaptor.forClass(com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBaseId()).isEqualTo(9L);
        assertThat(captor.getValue().getAgentId()).isEqualTo(1L);
    }

    @Test
    void createBaseInsertsCreatorSubscriptionRowExcludedFromLinkedAgentIds() {
        // Issue #209 UoW-1 补修：新建空间同步落创建者订阅行（与 V57 回填同口径）；
        // DTO 的 linkedAgentIds 仍排除创建者本人，前台无感。
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity.class)))
                .thenAnswer(invocation -> {
                    com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity inserted = invocation.getArgument(0);
                    inserted.setId(9L);
                    return 1;
                });
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity created =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        created.setId(9L);
        created.setCreatedByAgentId(1L);
        created.setName("kb");
        when(baseMapper.selectById(9L)).thenReturn(created);
        when(knowledgeMapper.selectCount(any())).thenReturn(0L);
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(subscription(9L, 1L)));

        var dto = service.createBase(
                1L, new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeBaseCreateRequest("kb"));

        ArgumentCaptor<com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity> captor =
                ArgumentCaptor.forClass(com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBaseId()).isEqualTo(9L);
        assertThat(captor.getValue().getAgentId()).isEqualTo(1L);
        assertThat(dto.agentId()).isEqualTo(1L);
        assertThat(dto.linkedAgentIds()).isEmpty();
    }

    @Test
    void deleteBaseOnEmptyBaseSweepsPartitionByBase() {
        // Issue #209 UoW-2：删库走整空间清理（deleteRetrievalDocumentsByBase），不再逐篇/逐分区处理
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(9L);
        base.setCreatedByAgentId(1L);
        base.setIsDefault(false);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectById(9L)).thenReturn(base);
        when(knowledgeMapper.selectCount(any())).thenReturn(0L);

        service.deleteBase(1L, 9L);

        verify(agentClient).deleteRetrievalDocumentsByBase("9");
        verify(subscriptionMapper).delete(any());
        verify(baseMapper).deleteById(9L);
    }

    @Test
    void deleteBaseRejectsNonEmptyBase() {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(9L);
        base.setCreatedByAgentId(1L);
        base.setIsDefault(false);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectById(9L)).thenReturn(base);
        when(knowledgeMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteBase(1L, 9L)).isInstanceOf(BusinessException.class);
        verify(baseMapper, never()).deleteById(any());
    }

    @Test
    void transcribeScannedPdfThrowsModeNoEntryWhenSelectedChainEmpty() {
        // 所选模式（KEY）链为空、CLI 链有入口：报模式无入口，绝不跨模式补 CLI 链（Issue #208）。
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of());

        assertThatThrownBy(() -> service.transcribeScannedPdf(7L, "KEY", new byte[] {1}))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.MODE_NO_AVAILABLE_ENTRY.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(ErrorCode.MODE_NO_AVAILABLE_ENTRY.getMessageKey());
                            assertThat(ex.getMessageArgs()).containsExactly("KEY");
                        });
        verify(modelEntryService, never()).selectionChain(anyLong(), eq("CLI"));
        verify(agentClient, never()).ocrPdf(any());
    }

    @Test
    void transcribeRejectsMissingOrInvalidMode() {
        for (String bad : new String[] {null, "", "  ", "AUTO"}) {
            assertThatThrownBy(() -> service.transcribeScannedPdf(7L, bad, new byte[] {1}))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            ex -> {
                                assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                                assertThat(ex.getMessageKey()).isEqualTo("admin.ai.kb.mode.invalid");
                            });
        }
        verify(modelEntryService, never()).selectionChain(anyLong(), any());
        verify(agentClient, never()).ocrPdf(any());
    }

    @Test
    void transcribeScannedPdfSendsChainAndReturnsTrimmedText() {
        AgentModelSelection primary = new AgentModelSelection("google:gemini", "k", null);
        AgentModelSelection fallback = new AgentModelSelection("google:gemini-b", "k2", null);
        byte[] pdf = "%PDF-fixture".getBytes(StandardCharsets.UTF_8);
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of(primary, fallback));
        // 严格模式下 when 式打桩本身会被计为一次可校验调用，captor+verify 会误报；
        // 改用 doAnswer 在打桩里直接截获请求参数，不再走 verify。
        java.util.concurrent.atomic.AtomicReference<AgentOcrPdfRequest> sent =
                new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(
                        invocation -> {
                            sent.set(invocation.getArgument(0));
                            return new AgentOcrPdfResult("  转写文本  ", "google:gemini");
                        })
                .when(agentClient)
                .ocrPdf(any());

        assertThat(service.transcribeScannedPdf(7L, "KEY", pdf)).isEqualTo("转写文本");

        assertThat(sent.get().model()).isEqualTo(primary);
        assertThat(sent.get().fallbackModels()).containsExactly(fallback);
        assertThat(sent.get().contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(pdf));
    }

    @Test
    void transcribeImageThrowsModeNoEntryWhenSelectedChainEmpty() {
        when(modelEntryService.selectionChain(7L, "CLI")).thenReturn(List.of());

        assertThatThrownBy(() -> service.transcribeImage(7L, "CLI", new byte[] {1}, "image/png"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.MODE_NO_AVAILABLE_ENTRY.getCode());
                            assertThat(ex.getMessageArgs()).containsExactly("CLI");
                        });
        verify(modelEntryService, never()).selectionChain(anyLong(), eq("KEY"));
        verify(agentClient, never()).ocrImage(any());
    }

    @Test
    void transcribeImageSendsChainMediaTypeAndReturnsTrimmedText() {
        AgentModelSelection primary = new AgentModelSelection("google:gemini", "k", null);
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        // 选 CLI 就只走 CLI 链（小写入参先归一化），KEY 链不被咨询。
        when(modelEntryService.selectionChain(7L, "CLI")).thenReturn(List.of(primary));
        java.util.concurrent.atomic.AtomicReference<
                        com.mywebsite.agent.dto.AgentOcrImageRequest>
                sent = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(
                        invocation -> {
                            sent.set(invocation.getArgument(0));
                            return new AgentOcrPdfResult("  图片文字  ", "google:gemini");
                        })
                .when(agentClient)
                .ocrImage(any());

        assertThat(service.transcribeImage(7L, "cli", png, "image/png")).isEqualTo("图片文字");
        verify(modelEntryService, never()).selectionChain(anyLong(), eq("KEY"));
        assertThat(sent.get().model()).isEqualTo(primary);
        assertThat(sent.get().mediaType()).isEqualTo("image/png");
        assertThat(sent.get().contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(png));
    }

    @Test
    void transcribeScannedPdfMapsClientFailureToTranscribeFailed() {
        // 调用失败与「无入口」「返回空」分列：抛 FILE_TRANSCRIBE_FAILED（503），文案参数带失败性质。
        when(modelEntryService.selectionChain(7L, "KEY"))
                .thenReturn(List.of(new AgentModelSelection("google:gemini", "k", null)));
        when(agentClient.ocrPdf(any())).thenThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED));

        assertThatThrownBy(() -> service.transcribeScannedPdf(7L, "KEY", new byte[] {1}))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode())
                                    .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
                            assertThat(ex.getMessageKey()).isEqualTo("admin.ai.kb.file.transcribe.failed");
                            assertThat(ex.getMessageArgs())
                                    .containsExactly(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getMessageKey());
                        });
    }

    @Test
    void transcribeScannedPdfReturnsEmptyOnlyWhenResultTextEmpty() {
        // 只有转写结果本身为空才回空串（上传侧据此报 FILE_EMPTY），失败不再混进来。
        when(modelEntryService.selectionChain(7L, "KEY"))
                .thenReturn(List.of(new AgentModelSelection("google:gemini", "k", null)));
        when(agentClient.ocrPdf(any())).thenReturn(new AgentOcrPdfResult("   ", "google:gemini"));

        assertThat(service.transcribeScannedPdf(7L, "KEY", new byte[] {1})).isEmpty();
    }

    // ---------- index_embedding_spec 落库 ----------

    @Test
    void reindexRecordsOwnerSelectionSpecOnSuccess() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));

        var detail = service.reindexDocument(1L, 11L);

        assertThat(detail.indexEmbeddingSpec()).isEqualTo("openai:embed-a");
        assertThat(detail.indexStatus()).isEqualTo(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        assertThat(detail.indexedAt()).isNotNull();
    }

    @Test
    void reindexRecordsNullSpecWhenSelectionIsNull() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of());

        var detail = service.reindexDocument(1L, 11L);

        assertThat(detail.indexEmbeddingSpec()).isNull();
        assertThat(detail.indexStatus()).isEqualTo(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        assertThat(detail.indexedAt()).isNotNull();
    }

    @Test
    void failedReindexDoesNotOverwritePreviouslyRecordedSpec() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));

        service.reindexDocument(1L, 11L);
        assertThat(doc.getIndexEmbeddingSpec()).isEqualTo("openai:embed-a");

        // 第二次同步时所有者分区 upsert 失败：FAILED 不得覆盖已记录的 spec。
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED))
                .when(agentClient)
                .upsertRetrievalDocument(any());

        var detail = service.reindexDocument(1L, 11L);

        assertThat(detail.indexStatus()).isEqualTo(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED);
        assertThat(detail.indexEmbeddingSpec()).isEqualTo("openai:embed-a");
    }

    @Test
    void nonActiveStatusClearsRecordedSpec() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        doc.setIndexedAt(OffsetDateTime.now());
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(knowledgeMapper.updateById(any(AdminAiKnowledgeDocumentEntity.class))).thenReturn(1);

        var detail = service.updateDocument(
                1L,
                11L,
                new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest(
                        "t2", "c2", AdminAiKnowledgeDocumentEntity.STATUS_DISABLED, null));

        assertThat(detail.indexStatus()).isEqualTo(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        assertThat(detail.indexEmbeddingSpec()).isNull();
        assertThat(detail.indexedAt()).isNull();
        assertThat(detail.indexStale()).isFalse();
    }

    // ---------- indexStale 检出 ----------

    @Test
    void detailIsStaleWhenRecordedSpecDiffersFromCurrentSelection() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-b")));

        assertThat(service.getDocument(1L, 11L).indexStale()).isTrue();
    }

    @Test
    void detailIsNotStaleWhenSpecsMatch() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));

        assertThat(service.getDocument(1L, 11L).indexStale()).isFalse();
    }

    @Test
    void detailIsNotStaleWhenNotYetIndexed() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));

        assertThat(service.getDocument(1L, 11L).indexStale()).isFalse();
        verify(modelEntryService, never()).embeddingClassesForSpace(anyLong());
    }

    @Test
    void reindexClearsStaleOnceSpecMatchesCurrentSelection() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-b")));

        assertThat(service.getDocument(1L, 11L).indexStale()).isTrue();

        var detail = service.reindexDocument(1L, 11L);

        assertThat(detail.indexStale()).isFalse();
        assertThat(detail.indexEmbeddingSpec()).isEqualTo("openai:embed-b");
    }

    @Test
    void overviewStaleDocumentsCountMatchesPerDocumentDetermination() {
        AdminAiKnowledgeDocumentEntity staleDoc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        staleDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        staleDoc.setIndexEmbeddingSpec("openai:embed-a");
        AdminAiKnowledgeDocumentEntity freshDoc = document(1L, 12L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        freshDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        freshDoc.setIndexEmbeddingSpec("openai:embed-b");
        AdminAiKnowledgeDocumentEntity failedDoc = document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        failedDoc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(staleDoc, freshDoc, failedDoc));
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-b")));

        var overview = service.knowledgeOverview();

        // staleDoc 记录 embed-a、当前 selection 是 embed-b → stale；freshDoc 记录与当前一致 → 不 stale；
        // failedDoc 非 INDEXED，恒不计入。
        assertThat(overview.staleDocuments()).isEqualTo(1);
        verify(modelEntryService, times(1)).embeddingClassesForSpace(901L);
    }

    @Test
    void detailIsStaleWhenANewEmbeddingClassIsRegistered() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClassesForSpace(901L);

        assertThat(service.getDocument(1L, 11L).indexStale()).isTrue();
    }

    @Test
    void reindexFillsOnlyTheMissingClassAndRecordsBoth() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClassesForSpace(901L);

        var detail = service.reindexDocument(1L, 11L);

        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().embeddingClasses())
                .extracting(AgentEmbeddingClass::embeddingSpec)
                .containsExactly("openai:embed-b");
        assertThat(detail.indexEmbeddingSpec()).isEqualTo("openai:embed-a\nopenai:embed-b");
        assertThat(detail.indexStatus()).isEqualTo(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        assertThat(detail.indexStale()).isFalse();
    }

    @Test
    void changedContentRewritesEveryRegisteredClass() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(knowledgeMapper.updateById(any(AdminAiKnowledgeDocumentEntity.class))).thenReturn(1);
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClassesForSpace(901L);

        service.updateDocument(
                1L,
                11L,
                new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeUpdateRequest(
                        "title-11", "changed", AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE, null));

        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().embeddingClasses())
                .extracting(AgentEmbeddingClass::embeddingSpec)
                .containsExactly("openai:embed-a", "openai:embed-b");
        // 单值字段取登记类的首元（classes.get(0)）：旧 embeddingSelection 退役后由这里钉死。
        assertThat(captor.getValue().embeddingSpec()).isEqualTo("openai:embed-a");
        assertThat(captor.getValue().apiKey()).isEqualTo("key");
    }

    @Test
    void reembedSkipsLegacyRecreateWhenASiteHasMultipleClasses() {
        when(knowledgeMapper.selectList(any()))
                .thenReturn(List.of(document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClassesForSpace(901L);

        var result = service.reembedAll();

        assertThat(result.recreatedSchema()).isFalse();
        assertThat(result.dimensions()).isNull();
        assertThat(result.documents()).isEqualTo(1);
        verify(agentClient, never()).prepareReembed(any(), org.mockito.ArgumentMatchers.anyBoolean());
        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().embeddingClasses())
                .extracting(AgentEmbeddingClass::embeddingSpec)
                .containsExactly("openai:embed-a", "openai:embed-b");
    }

    // ------------------------------------------------------------------
    // Issue #209 UoW-5：全量重建＋对账（rebuildAllSpaces）
    // ------------------------------------------------------------------

    @Test
    void rebuildSweepsLegacyAgentPartitionsBeforeRebuildingSpaces() {
        // 顺序钉死：旧分区（键＝agent id）清扫必须先于按空间重建——agent id 与 base id
        // 数值相撞时，相撞空间的真分区只能靠清扫之后的步骤二重建回来。
        com.mywebsite.admin.entity.AdminAgentEntity agent1 = new com.mywebsite.admin.entity.AdminAgentEntity();
        agent1.setId(1L);
        when(adminAgentMapper.selectList(any())).thenReturn(List.of(agent1));
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(doc));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));
        when(agentClient.retrievalPartitionStats("901"))
                .thenReturn(new AgentRetrievalPartitionStats("901", List.of("kb:11"), 5L));
        when(agentClient.retrievalPartitionStats("1"))
                .thenReturn(new AgentRetrievalPartitionStats("1", List.of(), 0L));

        var report = service.rebuildAllSpaces();

        InOrder order = inOrder(agentClient);
        order.verify(agentClient).deleteRetrievalDocumentsByBase("1");
        order.verify(agentClient).upsertRetrievalDocument(any());
        assertThat(report.spaces()).hasSize(1);
        var space = report.spaces().getFirst();
        assertThat(space.baseId()).isEqualTo(901L);
        assertThat(space.documents()).isEqualTo(1L);
        assertThat(space.indexedSources()).isEqualTo(1L);
        assertThat(space.chunks()).isEqualTo(5L);
        assertThat(space.syncFailures()).isEqualTo(0L);
        assertThat(report.totalDocuments()).isEqualTo(1L);
        assertThat(report.totalChunks()).isEqualTo(5L);
        assertThat(report.totalSpecs()).isEqualTo(1L);
        assertThat(report.totalSyncFailures()).isEqualTo(0L);
        assertThat(report.legacyResidues()).isEmpty();
    }

    @Test
    void rebuildCountsSyncFailurePerSpaceWithoutAbortingOthers() {
        // 单空间同步失败只计入该空间行与全局合计，不中断其余空间的重建。
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L), base(902L, 2L)));
        AdminAiKnowledgeDocumentEntity okDoc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        AdminAiKnowledgeDocumentEntity badDoc = document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(okDoc), List.of(badDoc));
        when(modelEntryService.embeddingClassesForSpace(901L)).thenReturn(List.of(selection("openai:embed-a")));
        when(modelEntryService.embeddingClassesForSpace(902L)).thenReturn(List.of(selection("openai:embed-a")));
        // 先给全量兜底 stub，再叠 902 定向抛错——否则严格模式会对 901 的调用报 PotentialStubbingProblem
        org.mockito.Mockito.doNothing().when(agentClient).upsertRetrievalDocument(any());
        doThrow(new RuntimeException("index down"))
                .when(agentClient)
                .upsertRetrievalDocument(argThat(payload -> "902".equals(payload.baseId())));
        when(agentClient.retrievalPartitionStats("901"))
                .thenReturn(new AgentRetrievalPartitionStats("901", List.of("kb:11"), 4L));
        when(agentClient.retrievalPartitionStats("902"))
                .thenReturn(new AgentRetrievalPartitionStats("902", List.of(), 0L));

        var report = service.rebuildAllSpaces();

        assertThat(report.spaces()).hasSize(2);
        assertThat(report.spaces().get(0).syncFailures()).isEqualTo(0L);
        assertThat(report.spaces().get(1).syncFailures()).isEqualTo(1L);
        assertThat(report.totalDocuments()).isEqualTo(2L);
        assertThat(report.totalChunks()).isEqualTo(4L);
        assertThat(report.totalSpecs()).isEqualTo(1L);
        assertThat(report.totalSyncFailures()).isEqualTo(1L);
        assertThat(report.legacyResidues()).isEmpty();
    }

    @Test
    void rebuildReportsLegacyResidueForPartitionKeyThatIsNotASpace() {
        // 残留口径：键不是现存空间、且清扫＋重建后索引源数仍非 0 → 列入残留清单；
        // 是现存空间的键下有内容属重建成果，不算残留（本用例无空间，键 7 纯残留）。
        com.mywebsite.admin.entity.AdminAgentEntity agent7 = new com.mywebsite.admin.entity.AdminAgentEntity();
        agent7.setId(7L);
        when(adminAgentMapper.selectList(any())).thenReturn(List.of(agent7));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(agentClient.retrievalPartitionStats("7"))
                .thenReturn(new AgentRetrievalPartitionStats("7", List.of("kb:99"), 3L));

        var report = service.rebuildAllSpaces();

        verify(agentClient).deleteRetrievalDocumentsByBase("7");
        assertThat(report.spaces()).isEmpty();
        assertThat(report.totalDocuments()).isEqualTo(0L);
        assertThat(report.legacyResidues()).hasSize(1);
        assertThat(report.legacyResidues().getFirst().partitionKey()).isEqualTo(7L);
        assertThat(report.legacyResidues().getFirst().indexedSources()).isEqualTo(1L);
    }

    @Test
    void rebuildSweepsAndChecksResiduesForDeletedAgentsToo() {
        // 解耦深审：已软删 agent 的旧分区同样在清扫与残留检查的键集合内——
        // 否则删站后的孤儿分区既不清扫、也不进对账报告。
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        when(adminAgentMapper.selectDeletedAgentIds()).thenReturn(List.of(9L));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(agentClient.retrievalPartitionStats("9"))
                .thenReturn(new AgentRetrievalPartitionStats("9", List.of("kb:77"), 2L));

        var report = service.rebuildAllSpaces();

        verify(agentClient).deleteRetrievalDocumentsByBase("9");
        assertThat(report.legacyResidues()).hasSize(1);
        assertThat(report.legacyResidues().getFirst().partitionKey()).isEqualTo(9L);
        assertThat(report.chunksUnknownSpaces()).isEqualTo(0L);
    }

    @Test
    void rebuildMarksChunksUnknownWhenPartitionStatsFail() {
        // 解耦深审：单空间分区统计取不到时，该空间块数记 -1 且计入
        // chunksUnknownSpaces——汇总 totalChunks 是低报，报告须可辨识。
        when(adminAgentMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of(base(901L, 1L)));
        when(knowledgeMapper.selectList(any())).thenReturn(List.of());
        when(agentClient.retrievalPartitionStats("901")).thenThrow(new RuntimeException("stats down"));

        var report = service.rebuildAllSpaces();

        assertThat(report.spaces()).hasSize(1);
        assertThat(report.spaces().getFirst().chunks()).isEqualTo(-1L);
        assertThat(report.totalChunks()).isEqualTo(0L);
        assertThat(report.chunksUnknownSpaces()).isEqualTo(1L);
    }

    @Test
    void reindexOmitsSegmentsWhenStoredContentDiffersFromOriginal() throws Exception {
        byte[] bytes = workbookBytes("Hours", 1, "Day", "9:00-18:00");
        AdminAiKnowledgeDocumentEntity doc = spreadsheetDocument("edited by hand");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(objectStorageService.get("bucket", "key")).thenReturn(bytes);

        service.reindexDocument(1L, 11L);

        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().segments()).isNull();
        assertThat(captor.getValue().tables()).isNull();
        assertThat(captor.getValue().content()).isEqualTo("edited by hand");
        assertThat(captor.getValue().content()).doesNotContain("rowStart");
    }

    @Test
    void reindexAttachesRowSegmentsWhenStoredContentMatchesOriginal() throws Exception {
        byte[] bytes = workbookBytes("Hours", 4, "Day", "9:00-18:00");
        String stored = com.mywebsite.infra.knowledge.KnowledgeContentConverter.toStoredContent("xlsx", bytes);
        AdminAiKnowledgeDocumentEntity doc = spreadsheetDocument(stored);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(objectStorageService.get("bucket", "key")).thenReturn(bytes);

        service.reindexDocument(1L, 11L);

        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().content()).doesNotContain("rowStart");
        assertThat(captor.getValue().segments()).hasSize(1);
        assertThat(captor.getValue().segments().getFirst().sheet()).isEqualTo("Hours");
        assertThat(captor.getValue().segments().getFirst().rowStart()).isEqualTo(5);
        assertThat(captor.getValue().segments().getFirst().rowEnd()).isEqualTo(5);
        assertThat(captor.getValue().content()).isEqualTo(stored);
        assertThat(captor.getValue().tables()).hasSize(1);
        assertThat(captor.getValue().tables().getFirst().headerRow()).isEqualTo(5);
        assertThat(captor.getValue().tables().getFirst().rows()).isEmpty();
    }

    @Test
    void reindexAttachesTablesWithOriginalRowNumbersWhenContentMatches() throws Exception {
        byte[] bytes;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = workbook.createSheet("Hours");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Day");
            header.createCell(1).setCellValue("Hours");
            sheet.createRow(2);
            var data = sheet.createRow(4);
            data.createCell(0).setCellValue("Mon");
            data.createCell(1).setCellValue("8:00");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }
        String stored = com.mywebsite.infra.knowledge.KnowledgeContentConverter.toStoredContent("xlsx", bytes);
        AdminAiKnowledgeDocumentEntity doc = spreadsheetDocument(stored);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(baseMapper.selectById(901L)).thenReturn(base(901L, 1L));
        when(objectStorageService.get("bucket", "key")).thenReturn(bytes);

        service.reindexDocument(1L, 11L);

        ArgumentCaptor<AgentRetrievalDocument> captor = ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo(stored);
        assertThat(captor.getValue().tables()).hasSize(1);
        assertThat(captor.getValue().tables().getFirst().headerRow()).isEqualTo(1);
        assertThat(captor.getValue().tables().getFirst().rows()).hasSize(1);
        assertThat(captor.getValue().tables().getFirst().rows().getFirst().rowNumber()).isEqualTo(5);
    }

    private static AdminAiKnowledgeDocumentEntity spreadsheetDocument(String content) {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setContent(content);
        doc.setSourceFormat("xlsx");
        doc.setStorageBucket("bucket");
        doc.setStorageKey("key");
        return doc;
    }

    private static byte[] workbookBytes(String sheet, int rowIndex, String a, String b) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var row = workbook.createSheet(sheet).createRow(rowIndex);
            row.createCell(0).setCellValue(a);
            row.createCell(1).setCellValue(b);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static AdminAiKnowledgeDocumentEntity document(long creatorAgentId, long id, String status) {
        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setId(id);
        // 文档只属空间（Issue #209 UoW-5 起 agent_id 列已删）：给每篇文档一个确定
        // 空间（按创建者 agent 分开），需要特定空间的用例在 helper 后显式覆盖 setBaseId。
        entity.setBaseId(900L + creatorAgentId);
        entity.setTitle("title-" + id);
        entity.setContent("content-" + id);
        entity.setStatus(status);
        return entity;
    }
}
