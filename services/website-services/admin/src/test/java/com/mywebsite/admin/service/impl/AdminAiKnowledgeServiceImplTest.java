package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiKnowledgeService.ReembedConflictException;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentDimensionChangeRequiredException;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAiKnowledgeServiceImplTest {

    @Mock
    private AdminAiKnowledgeDocumentMapper knowledgeMapper;

    @Mock
    private AdminSiteMapper adminSiteMapper;

    @Mock
    private AgentClient agentClient;

    @Mock
    private AdminAiModelEntryService modelEntryService;

    private AdminAiKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiKnowledgeServiceImpl(
                knowledgeMapper, adminSiteMapper, agentClient, modelEntryService);
    }

    @Test
    void reembedCountsSitesAndActiveDocumentsWhenDimensionsMatch() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(
                document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(1L, 12L, AdminAiKnowledgeDocumentEntity.STATUS_DISABLED),
                document(2L, 21L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE),
                document(2L, 22L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(modelEntryService.currentSelection(1L)).thenReturn(selection);
        when(modelEntryService.currentSelection(2L)).thenReturn(selection);
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
        when(modelEntryService.currentSelection(1L)).thenReturn(first);
        when(modelEntryService.currentSelection(2L)).thenReturn(second);
        when(agentClient.prepareReembed(first, false)).thenReturn(new AgentReembedPrepareResult(3, 3, false));
        when(agentClient.prepareReembed(second, false))
                .thenThrow(new AgentDimensionChangeRequiredException(4, 3));

        assertThatThrownBy(() -> service.reembedAll())
                .isInstanceOf(ReembedConflictException.class)
                .satisfies(ex -> assertThat(((ReembedConflictException) ex).conflicts())
                        .extracting(conflict -> conflict.siteId() + ":" + conflict.dimensions())
                        .containsExactly("1:3", "2:4"));
        verify(agentClient, never()).prepareReembed(any(), eq(true));
        verify(agentClient, never()).upsertRetrievalDocument(any());
    }

    @Test
    void reembedConfirmsRecreateBeforeUpsertWhenColumnDimensionDiffers() {
        AgentModelSelection selection = selection("openai:embed-a");
        when(knowledgeMapper.selectList(any()))
                .thenReturn(List.of(document(1L, 11L, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)));
        when(modelEntryService.currentSelection(1L)).thenReturn(selection);
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
        when(modelEntryService.currentSelection(1L)).thenReturn(selection);
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
        assertThat(result.failed().getFirst().siteId()).isEqualTo(1L);
        assertThat(result.failed().getFirst().sourceId()).isEqualTo("kb:22");
        assertThat(result.failed().getFirst().error())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getMessageKey());
    }

    private static AgentModelSelection selection(String embeddingSpec) {
        return new AgentModelSelection("openai:gpt", "key", null, embeddingSpec);
    }

    private static AdminAiKnowledgeDocumentEntity document(long siteId, long id, String status) {
        AdminAiKnowledgeDocumentEntity entity = new AdminAiKnowledgeDocumentEntity();
        entity.setId(id);
        entity.setSiteId(siteId);
        entity.setTitle("title-" + id);
        entity.setContent("content-" + id);
        entity.setStatus(status);
        return entity;
    }
}
