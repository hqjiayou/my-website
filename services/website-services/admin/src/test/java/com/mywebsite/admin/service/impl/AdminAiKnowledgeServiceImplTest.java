package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private com.mywebsite.admin.mapper.AdminAiKnowledgeBaseLinkMapper linkMapper;

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
                linkMapper,
                adminAiStreamExecutor);
    }

    @Test
    void reembedCountsSitesAndActiveDocumentsWhenDimensionsMatch() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(1L, 12L, AdminAiKnowledgeDocumentEntity.STATUS_DISABLED),
                document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(2L, 22L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection));
        when(modelEntryService.embeddingClasses(2L)).thenReturn(List.of(selection));
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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(first));
        when(modelEntryService.embeddingClasses(2L)).thenReturn(List.of(second));
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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection));
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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection));
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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection));
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

    @Test
    void reindexFailedRetriesAndCountsSuccesses() {
        AdminAiKnowledgeDocumentEntity failed = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        failed.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_FAILED);
        AdminAiKnowledgeDocumentEntity refreshed = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        refreshed.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(failed));
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-a")));
        when(knowledgeMapper.selectById(11L)).thenReturn(refreshed);
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        var result = service.reindexFailedDocuments();

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        verify(agentClient).upsertRetrievalDocument(any());
    }

    @Test
    void linkBaseFansOutActiveDocumentsToTargetPartition() {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(9L);
        base.setAgentId(1L);
        when(baseMapper.selectById(9L)).thenReturn(base);
        com.mywebsite.admin.entity.AdminAgentEntity target = new com.mywebsite.admin.entity.AdminAgentEntity();
        target.setId(2L);
        when(adminAgentMapper.selectById(2L)).thenReturn(target);
        when(linkMapper.selectOne(any())).thenReturn(null);
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setBaseId(9L);
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(doc));
        when(modelEntryService.embeddingClasses(2L)).thenReturn(List.of(selection("openai:embed-a")));

        service.linkBase(9L, 2L);

        verify(linkMapper).insert(any(com.mywebsite.admin.entity.AdminAiKnowledgeBaseLinkEntity.class));
        var captor = org.mockito.ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getValue().agentId()).isEqualTo("2");
    }

    @Test
    void createDocumentSyncsToOwnerAndLinkedPartitions() {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity defaultBase =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        defaultBase.setId(9L);
        defaultBase.setAgentId(1L);
        defaultBase.setIsDefault(true);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectOne(any())).thenReturn(defaultBase);
        when(knowledgeMapper.insert(any(AdminAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiKnowledgeDocumentEntity inserted = invocation.getArgument(0);
                    inserted.setId(11L);
                    return 1;
                });
        AdminAiKnowledgeDocumentEntity saved = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        saved.setBaseId(9L);
        when(knowledgeMapper.selectById(any())).thenReturn(saved);
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseLinkEntity link =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseLinkEntity();
        link.setBaseId(9L);
        link.setAgentId(3L);
        when(linkMapper.selectList(any())).thenReturn(List.of(link));
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-a")));
        when(modelEntryService.embeddingClasses(3L)).thenReturn(List.of(selection("openai:embed-a")));
        when(knowledgeMapper.update(any(), any())).thenReturn(1);

        service.createDocument(
                1L,
                new com.mywebsite.admin.dto.AdminAiKnowledgeDtos.AdminAiKnowledgeCreateRequest("t", "c"));

        var captor = org.mockito.ArgumentCaptor.forClass(AgentRetrievalDocument.class);
        verify(agentClient, times(2)).upsertRetrievalDocument(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentRetrievalDocument::agentId)
                .containsExactlyInAnyOrder("1", "3");
    }

    @Test
    void deleteBaseRejectsNonEmptyBase() {
        com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity base =
                new com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity();
        base.setId(9L);
        base.setAgentId(1L);
        base.setIsDefault(false);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(baseMapper.selectById(9L)).thenReturn(base);
        when(knowledgeMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteBase(1L, 9L)).isInstanceOf(BusinessException.class);
        verify(baseMapper, never()).deleteById(any());
    }

    @Test
    void transcribeScannedPdfReturnsEmptyWhenChainEmpty() {
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of());
        when(modelEntryService.selectionChain(7L, "CLI")).thenReturn(List.of());

        assertThat(service.transcribeScannedPdf(7L, new byte[] {1})).isEmpty();
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

        assertThat(service.transcribeScannedPdf(7L, pdf)).isEqualTo("转写文本");

        assertThat(sent.get().model()).isEqualTo(primary);
        assertThat(sent.get().fallbackModels()).containsExactly(fallback);
        assertThat(sent.get().contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(pdf));
    }

    @Test
    void transcribeImageReturnsEmptyWhenChainEmpty() {
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of());
        when(modelEntryService.selectionChain(7L, "CLI")).thenReturn(List.of());

        assertThat(service.transcribeImage(7L, new byte[] {1}, "image/png")).isEmpty();
        verify(agentClient, never()).ocrImage(any());
    }

    @Test
    void transcribeImageSendsChainMediaTypeAndReturnsTrimmedText() {
        AgentModelSelection primary = new AgentModelSelection("google:gemini", "k", null);
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of(primary));
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

        assertThat(service.transcribeImage(7L, png, "image/png")).isEqualTo("图片文字");
        assertThat(sent.get().model()).isEqualTo(primary);
        assertThat(sent.get().mediaType()).isEqualTo("image/png");
        assertThat(sent.get().contentBase64()).isEqualTo(Base64.getEncoder().encodeToString(png));
    }

    @Test
    void transcribeScannedPdfSwallowsClientFailure() {
        when(modelEntryService.selectionChain(7L, "KEY"))
                .thenReturn(List.of(new AgentModelSelection("google:gemini", "k", null)));
        when(agentClient.ocrPdf(any())).thenThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED));

        assertThat(service.transcribeScannedPdf(7L, new byte[] {1})).isEmpty();
    }

    // ---------- index_embedding_spec 落库 ----------

    @Test
    void reindexRecordsOwnerSelectionSpecOnSuccess() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-a")));

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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of());

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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-a")));

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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-b")));

        assertThat(service.getDocument(1L, 11L).indexStale()).isTrue();
    }

    @Test
    void detailIsNotStaleWhenSpecsMatch() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-a")));

        assertThat(service.getDocument(1L, 11L).indexStale()).isFalse();
    }

    @Test
    void detailIsNotStaleWhenNotYetIndexed() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_PENDING);
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);

        assertThat(service.getDocument(1L, 11L).indexStale()).isFalse();
        verify(modelEntryService, never()).embeddingClasses(anyLong());
    }

    @Test
    void reindexClearsStaleOnceSpecMatchesCurrentSelection() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-b")));

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
        when(modelEntryService.embeddingClasses(1L)).thenReturn(List.of(selection("openai:embed-b")));

        var overview = service.knowledgeOverview();

        // staleDoc 记录 embed-a、当前 selection 是 embed-b → stale；freshDoc 记录与当前一致 → 不 stale；
        // failedDoc 非 INDEXED，恒不计入。
        assertThat(overview.staleDocuments()).isEqualTo(1);
        verify(modelEntryService, times(1)).embeddingClasses(1L);
    }

    @Test
    void detailIsStaleWhenANewEmbeddingClassIsRegistered() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClasses(1L);

        assertThat(service.getDocument(1L, 11L).indexStale()).isTrue();
    }

    @Test
    void reindexFillsOnlyTheMissingClassAndRecordsBoth() {
        AdminAiKnowledgeDocumentEntity doc = document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        doc.setIndexStatus(AdminAiKnowledgeDocumentEntity.INDEX_STATUS_INDEXED);
        doc.setIndexEmbeddingSpec("openai:embed-a");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClasses(1L);

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
        when(knowledgeMapper.updateById(any(AdminAiKnowledgeDocumentEntity.class))).thenReturn(1);
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClasses(1L);

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
        doReturn(List.of(selection("openai:embed-a"), selection("openai:embed-b")))
                .when(modelEntryService)
                .embeddingClasses(1L);

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

    @Test
    void reindexOmitsSegmentsWhenStoredContentDiffersFromOriginal() throws Exception {
        byte[] bytes = workbookBytes("Hours", 1, "Day", "9:00-18:00");
        AdminAiKnowledgeDocumentEntity doc = spreadsheetDocument("edited by hand");
        when(adminAgentMapper.selectById(1L)).thenReturn(new com.mywebsite.admin.entity.AdminAgentEntity());
        when(knowledgeMapper.selectById(11L)).thenReturn(doc);
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

    private static AdminAiKnowledgeDocumentEntity document(long agentId, long id, String status) {
        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setId(id);
        entity.setAgentId(agentId);
        entity.setTitle("title-" + id);
        entity.setContent("content-" + id);
        entity.setStatus(status);
        return entity;
    }
}
