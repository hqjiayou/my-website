package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.entity.AdminAiMessageEntity;
import com.mywebsite.admin.mapper.AdminAiMessageMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentExperienceExtractRequest;
import com.mywebsite.agent.dto.AgentExperienceExtractResult;
import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentUsageService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 提炼管线编排（#184 UoW-2）：信号判定、预筛命中/不命中、分组落库、失败隔离与记账。
 * 执行层与 Mapper 全 mock，agent_meta 用真实 JSON 形状过一遍反序列化。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiExperienceExtractionServiceImplTest {

    private static final String META_TOOL_FAILURE =
            """
            {"usage":{"requests":1,"toolCalls":2,"inputTokens":10,"outputTokens":5},
             "tools":[{"name":"query_table","ok":false}],"skillsApplied":[],"searches":[]}
            """;
    private static final String META_EMPTY_SEARCH =
            """
            {"usage":{"requests":1,"toolCalls":1,"inputTokens":10,"outputTokens":5},
             "tools":[],"skillsApplied":[],"searches":[{"query":"工资单","hitCount":0}]}
            """;
    private static final String META_HEAVY =
            """
            {"usage":{"requests":4,"toolCalls":12,"inputTokens":10,"outputTokens":5},
             "tools":[{"name":"search_knowledge","ok":true}],"skillsApplied":[],"searches":[]}
            """;
    private static final String META_CLEAN =
            """
            {"usage":{"requests":1,"toolCalls":1,"inputTokens":10,"outputTokens":5},
             "tools":[{"name":"search_knowledge","ok":true}],"skillsApplied":[],
             "searches":[{"query":"x","hitCount":3}]}
            """;

    @Mock private AdminAiMessageMapper messageMapper;
    @Mock private AdminAiModelEntryService modelEntryService;
    @Mock private AgentClient agentClient;
    @Mock private AgentExperienceLessonService lessonService;
    @Mock private AgentUsageService usageService;

    private AdminAiExperienceExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiExperienceExtractionServiceImpl(
                messageMapper, modelEntryService, agentClient, lessonService, usageService, new ObjectMapper());
        AdminAiMessageEntity user = message(100L, 55L, 7L, AdminAiMessageEntity.ROLE_USER, "user question", null);
        lenient().when(messageMapper.selectOne(any())).thenReturn(user);
        lenient().when(lessonService.listApproved()).thenReturn(List.of());
        lenient().when(lessonService.listByStatus(AgentExperienceLessonEntity.STATUS_PENDING)).thenReturn(List.of());
        lenient().when(lessonService.createCandidate(any())).thenReturn(new AgentExperienceLessonEntity());
        lenient()
                .when(modelEntryService.chainCandidates(any(Long.class), any()))
                .thenReturn(List.of(new ChainCandidate(11L, "entry", "fake:key")));
    }

    private static AdminAiMessageEntity message(
            long id, long conversationId, Long agentId, String role, String content, String agentMeta) {
        AdminAiMessageEntity entity = new AdminAiMessageEntity();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setAgentId(agentId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setAgentMeta(agentMeta);
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }

    private static AdminAiMessageEntity assistant(long id, long agentId, String agentMeta) {
        return message(id, 55L, agentId, AdminAiMessageEntity.ROLE_ASSISTANT, "assistant reply", agentMeta);
    }

    private void stubKeyChain(long agentId) {
        when(modelEntryService.selectionChain(agentId, "KEY"))
                .thenReturn(List.of(new AgentModelSelection("fake:key", "secret", null)));
    }

    @Test
    void signalsOfDetectsEachSignalFromRealMetaFields() {
        assertThat(AdminAiExperienceExtractionServiceImpl.signalsOf(meta(META_TOOL_FAILURE)))
                .containsExactly(AdminAiExperienceExtractionServiceImpl.SIGNAL_TOOL_FAILURE);
        assertThat(AdminAiExperienceExtractionServiceImpl.signalsOf(meta(META_EMPTY_SEARCH)))
                .containsExactly(AdminAiExperienceExtractionServiceImpl.SIGNAL_EMPTY_SEARCH);
        assertThat(AdminAiExperienceExtractionServiceImpl.signalsOf(meta(META_HEAVY)))
                .containsExactly(AdminAiExperienceExtractionServiceImpl.SIGNAL_HEAVY_TOOL_USE);
        assertThat(AdminAiExperienceExtractionServiceImpl.signalsOf(meta(META_CLEAN))).isEmpty();
        assertThat(AdminAiExperienceExtractionServiceImpl.signalsOf(null)).isEmpty();
    }

    private static AgentRunMeta meta(String json) {
        try {
            return new ObjectMapper()
                    .readerFor(AgentRunMeta.class)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void prefilterSkipsTurnsWithoutSignals() {
        when(messageMapper.selectList(any()))
                .thenReturn(List.of(assistant(101L, 7L, META_TOOL_FAILURE), assistant(102L, 7L, META_CLEAN)));
        stubKeyChain(7L);
        when(agentClient.extractExperience(any()))
                .thenReturn(new AgentExperienceExtractResult(List.of(), null, "fake:key"));

        AdminAiExperienceExtractionResultDto result = service.extractRecent(new AdminAiExperienceExtractionRequestDto(null, null));

        assertThat(result.scannedTurns()).isEqualTo(1);
        assertThat(result.extractionCalls()).isEqualTo(1);
        assertThat(result.producedCandidates()).isZero();
        assertThat(result.failedTurns()).isZero();
        ArgumentCaptor<AgentExperienceExtractRequest> captor = ArgumentCaptor.forClass(AgentExperienceExtractRequest.class);
        verify(agentClient).extractExperience(captor.capture());
        assertThat(captor.getValue().turns()).hasSize(1);
        assertThat(captor.getValue().turns().get(0).turnId()).isEqualTo("101");
        assertThat(captor.getValue().turns().get(0).userMessage()).isEqualTo("user question");
        assertThat(captor.getValue().turns().get(0).metaSummary()).contains("TOOL_FAILURE", "query_table");
    }

    @Test
    void batchPersistsCandidatesWithSourceIdsAndRecordsExperienceUsage() {
        AgentExperienceLessonEntity approved = new AgentExperienceLessonEntity();
        approved.setTitle("已批准教训");
        AgentExperienceLessonEntity pending = new AgentExperienceLessonEntity();
        pending.setTitle("待审候选");
        when(lessonService.listApproved()).thenReturn(List.of(approved));
        when(lessonService.listByStatus(AgentExperienceLessonEntity.STATUS_PENDING)).thenReturn(List.of(pending));
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant(101L, 7L, META_TOOL_FAILURE)));
        stubKeyChain(7L);
        when(agentClient.extractExperience(any()))
                .thenReturn(new AgentExperienceExtractResult(
                        List.of(
                                new AgentExperienceExtractResult.Candidate(
                                        "101", "工具失败不得编造", "症状", "失败时明示失败", "Turn 1 工具失败"),
                                new AgentExperienceExtractResult.Candidate(
                                        "999", "无来源候选", null, "应被丢弃", null)),
                        new AgentRunMeta.Usage(1, 0, 100, 50),
                        "fake:key"));

        AdminAiExperienceExtractionResultDto result = service.extractRecent(new AdminAiExperienceExtractionRequestDto(null, null));

        assertThat(result.producedCandidates()).isEqualTo(1);
        ArgumentCaptor<AgentExperienceLessonDraft> draftCaptor = ArgumentCaptor.forClass(AgentExperienceLessonDraft.class);
        verify(lessonService, times(1)).createCandidate(draftCaptor.capture());
        AgentExperienceLessonDraft draft = draftCaptor.getValue();
        assertThat(draft.title()).isEqualTo("工具失败不得编造");
        assertThat(draft.sourceAgentId()).isEqualTo(7L);
        assertThat(draft.sourceConversationId()).isEqualTo(55L);
        assertThat(draft.sourceMessageId()).isEqualTo(101L);

        ArgumentCaptor<AgentExperienceExtractRequest> requestCaptor =
                ArgumentCaptor.forClass(AgentExperienceExtractRequest.class);
        verify(agentClient).extractExperience(requestCaptor.capture());
        assertThat(requestCaptor.getValue().existingTitles()).containsExactly("已批准教训", "待审候选");
        assertThat(requestCaptor.getValue().model().spec()).isEqualTo("fake:key");

        ArgumentCaptor<AgentUsageRecord> usageCaptor = ArgumentCaptor.forClass(AgentUsageRecord.class);
        verify(usageService, times(1)).recordUsage(usageCaptor.capture());
        AgentUsageRecord usage = usageCaptor.getValue();
        assertThat(usage.source()).isEqualTo(AgentUsageService.SOURCE_EXPERIENCE);
        assertThat(usage.agentId()).isEqualTo(7L);
        assertThat(usage.entryId()).isEqualTo(11L);
        assertThat(usage.mode()).isEqualTo("KEY");
        assertThat(usage.promptTokens()).isEqualTo(100L);
        assertThat(usage.completionTokens()).isEqualTo(50L);
        assertThat(usage.status()).isEqualTo(AgentUsageService.STATUS_SUCCESS);
    }

    @Test
    void extractionFailureIsIsolatedPerAgentGroup() {
        when(messageMapper.selectList(any()))
                .thenReturn(List.of(assistant(101L, 7L, META_TOOL_FAILURE), assistant(201L, 8L, META_EMPTY_SEARCH)));
        stubKeyChain(7L);
        when(modelEntryService.selectionChain(8L, "KEY"))
                .thenReturn(List.of(new AgentModelSelection("fake:eight", "secret", null)));
        when(agentClient.extractExperience(any()))
                .thenAnswer(invocation -> {
                    AgentExperienceExtractRequest request = invocation.getArgument(0);
                    if (request.turns().get(0).turnId().equals("101")) {
                        throw new RuntimeException("agent-services down");
                    }
                    return new AgentExperienceExtractResult(
                            List.of(new AgentExperienceExtractResult.Candidate("201", "检索落空要明示", null, "无命中时说明", null)),
                            new AgentRunMeta.Usage(1, 0, 10, 5),
                            "fake:eight");
                });

        AdminAiExperienceExtractionResultDto result = service.extractRecent(new AdminAiExperienceExtractionRequestDto(null, null));

        assertThat(result.scannedTurns()).isEqualTo(2);
        assertThat(result.extractionCalls()).isEqualTo(2);
        assertThat(result.failedTurns()).isEqualTo(1);
        assertThat(result.producedCandidates()).isEqualTo(1);
        // 失败组不记账，只有成功的一组落一行
        verify(usageService, times(1)).recordUsage(any());
    }

    @Test
    void emptyChainCountsTurnsAsFailedWithoutCallingClient() {
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant(101L, 7L, META_HEAVY)));
        when(modelEntryService.selectionChain(7L, "KEY")).thenReturn(List.of());
        when(modelEntryService.selectionChain(7L, "CLI")).thenReturn(List.of());

        AdminAiExperienceExtractionResultDto result = service.extractRecent(new AdminAiExperienceExtractionRequestDto(null, null));

        assertThat(result.scannedTurns()).isEqualTo(1);
        assertThat(result.failedTurns()).isEqualTo(1);
        assertThat(result.extractionCalls()).isZero();
        verify(agentClient, never()).extractExperience(any());
        verify(usageService, never()).recordUsage(any());
    }

    @Test
    void missingUsageRecordsNullTokens() {
        when(messageMapper.selectList(any())).thenReturn(List.of(assistant(101L, 7L, META_TOOL_FAILURE)));
        stubKeyChain(7L);
        when(agentClient.extractExperience(any()))
                .thenReturn(new AgentExperienceExtractResult(List.of(), null, "fake:key"));

        service.extractRecent(new AdminAiExperienceExtractionRequestDto(null, null));

        ArgumentCaptor<AgentUsageRecord> usageCaptor = ArgumentCaptor.forClass(AgentUsageRecord.class);
        verify(usageService, times(1)).recordUsage(usageCaptor.capture());
        assertThat(usageCaptor.getValue().promptTokens()).isNull();
        assertThat(usageCaptor.getValue().completionTokens()).isNull();
        assertThat(usageCaptor.getValue().source()).isEqualTo(AgentUsageService.SOURCE_EXPERIENCE);
    }
}
