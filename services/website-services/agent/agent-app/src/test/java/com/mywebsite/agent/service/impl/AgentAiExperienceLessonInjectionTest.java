package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentExperienceLessonItem;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.service.LocalizedMessageService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 正式对话把已批准经验条目注入请求（Issue #184 UoW-4）：有条目带
 * [{lessonNo, lesson}]、无条目不带字段、查库失败不拖垮对话。
 * 同步 chat 与流式 chatStream 共用 buildAgentRequest，本类以同步链验组装点。
 */
@ExtendWith(MockitoExtension.class)
class AgentAiExperienceLessonInjectionTest {

    @Mock private AgentClient agentClient;

    @Mock private AgentProperties agentProperties;

    @Mock private ModelEntryCipher modelEntryCipher;

    @Mock private AgentAiRateLimitService rateLimitService;

    @Mock private AgentAiConversationService conversationService;

    @Mock private AgentUserMemoryService memoryService;

    @Mock private AgentAiModelEntryMapper modelEntryMapper;

    @Mock private AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock private LocalizedMessageService localizedMessageService;

    @Mock private ExecutorService agentAiStreamExecutor;

    @Mock private AgentExperienceLessonService experienceLessonService;

    private AgentAiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentAiServiceImpl(
                agentClient,
                agentProperties,
                modelEntryCipher,
                rateLimitService,
                conversationService,
                memoryService,
                modelEntryMapper,
                knowledgeDocumentMapper,
                localizedMessageService,
                agentAiStreamExecutor,
                new ObjectMapper(),
                Mockito.mock(AgentUsageService.class),
                Mockito.mock(AgentEmbeddingClassService.class),
                experienceLessonService,
                Mockito.mock(com.mywebsite.agent.service.AgentMcpSelectionService.class),
                Mockito.mock(KnowledgeSpaceResolver.class));
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        // 片段回退在无可达空间时短路不查文档（Issue #209 UoW-4），本 stub 只在有空间的用例才被消费。
        Mockito.lenient().when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        when(memoryService.recentContents(10L, 200L)).thenReturn(List.of());
        AgentAiConversationEntity conversation = new AgentAiConversationEntity();
        conversation.setId(77L);
        conversation.setAgentId(10L);
        conversation.setAgentUserId(200L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(conversation, List.of(), null, List.of(), null));
        when(agentClient.chat(any()))
                .thenReturn(new AgentChatResponse(
                        "reply", "77", "LIVE", List.of(), AgentRunMeta.EMPTY, List.of(), List.of()));
        AgentAiModelEntryEntity entry = new AgentAiModelEntryEntity();
        entry.setId(3L);
        entry.setAgentId(10L);
        entry.setModelSpec("google:test-model");
        entry.setApiKey("enc:key-3");
        entry.setEnabled(true);
        entry.setDefaultEntry(true);
        entry.setMode("KEY");
        when(modelEntryMapper.selectList(any())).thenReturn(List.of(entry), List.of());
        when(modelEntryCipher.decrypt("enc:key-3")).thenReturn("key-3");
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static AgentExperienceLessonEntity approved(int lessonNo, String lesson) {
        AgentExperienceLessonEntity entity = new AgentExperienceLessonEntity();
        entity.setLessonNo(lessonNo);
        entity.setLesson(lesson);
        entity.setStatus(AgentExperienceLessonEntity.STATUS_APPROVED);
        return entity;
    }

    private AgentChatRequest chatAndCapture() {
        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY"));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        return captor.getValue();
    }

    @Test
    void chatInjectsApprovedLessonsInLessonNoOrder() {
        when(experienceLessonService.listApproved())
                .thenReturn(List.of(approved(6, "先枚举再逐份抽取"), approved(7, "工资默认实发口径")));

        AgentChatRequest request = chatAndCapture();

        assertThat(request.experienceLessons())
                .containsExactly(
                        new AgentExperienceLessonItem(6, "先枚举再逐份抽取"),
                        new AgentExperienceLessonItem(7, "工资默认实发口径"));
    }

    @Test
    void chatOmitsLessonsFieldWhenNoneApproved() {
        when(experienceLessonService.listApproved()).thenReturn(List.of());

        AgentChatRequest request = chatAndCapture();

        assertThat(request.experienceLessons()).isNull();
    }

    @Test
    void chatSurvivesLessonLookupFailure() {
        when(experienceLessonService.listApproved())
                .thenThrow(new RuntimeException("db down"));

        AgentChatRequest request = chatAndCapture();

        assertThat(request.experienceLessons()).isNull();
    }
}
