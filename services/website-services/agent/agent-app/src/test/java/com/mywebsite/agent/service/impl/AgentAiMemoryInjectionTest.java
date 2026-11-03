package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.client.AgentStreamListener;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.service.LocalizedMessageService;
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 站点对话把最近 20 条记忆注入请求，并在 chat / stream 成功后落库抽取结果。 */
@ExtendWith(MockitoExtension.class)
class AgentAiMemoryInjectionTest {

    @Mock
    private AgentClient agentClient;

    @Mock
    private AgentProperties agentProperties;

    @Mock
    private ModelEntryCipher modelEntryCipher;

    @Mock
    private AgentAiRateLimitService rateLimitService;

    @Mock
    private AgentAiConversationService conversationService;

    @Mock
    private AgentUserMemoryService memoryService;

    @Mock
    private AgentAiModelEntryMapper modelEntryMapper;

    @Mock
    private AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private LocalizedMessageService localizedMessageService;

    @Mock
    private ExecutorService agentAiStreamExecutor;

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
                org.mockito.Mockito.mock(AgentUsageService.class),
                org.mockito.Mockito.mock(AgentEmbeddingClassService.class),
                org.mockito.Mockito.mock(AgentExperienceLessonService.class));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void chatInjectsTwentyMemoriesAndStoresExtractedAfterTheReply() {
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        AgentAiConversationEntity conversation = conversation(77L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(conversation, List.of(), null, List.of()));
        List<String> memories = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            memories.add("m" + i);
        }
        when(memoryService.recentContents(10L, 200L)).thenReturn(memories);
        when(agentClient.chat(any()))
                .thenReturn(new AgentChatResponse(
                        "reply",
                        "77",
                        "LIVE",
                        List.of(),
                        AgentRunMeta.EMPTY,
                        List.of(),
                        List.of("  likes   tea  ")));
        // 模式必填且该模式要有可用入口（Issue #145）：给一条 KEY 默认入口
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

        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY"));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        assertThat(captor.getValue().memories()).containsExactlyElementsOf(memories);
        assertThat(captor.getValue().memoryEnabled()).isTrue();
        InOrder order = inOrder(conversationService, memoryService);
        order.verify(conversationService)
                .recordExchange(any(), any(), any(), any(), any(), any(), any(), any(), any());
        order.verify(memoryService)
                .storeExtracted(10L, 200L, 77L, List.of("  likes   tea  "));
    }

    @Test
    void streamDoneStoresExtractedMemoriesWithTheReply() {
        AgentAiConversationEntity conversation = conversation(77L);
        ChatPreparation preparation = new ChatPreparation(conversation, List.of(), null, List.of());
        doAnswer(invocation -> {
                    AgentStreamListener listener = invocation.getArgument(1);
                    listener.onEvent(
                            "DONE",
                            "{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"extractedMemories\":[\"likes tea\"]}");
                    return null;
                })
                .when(agentClient)
                .chatStream(any(), any());

        service.forwardStream(
                10L,
                200L,
                preparation,
                "hello",
                List.of(),
                new AgentChatRequest("10", "hello"),
                "KEY",
                org.mockito.Mockito.mock(SseEmitter.class),
                List.of());

        verify(memoryService).storeExtracted(10L, 200L, 77L, List.of("likes tea"));
    }

    private static AgentAiConversationEntity conversation(long id) {
        AgentAiConversationEntity entity = new AgentAiConversationEntity();
        entity.setId(id);
        entity.setAgentId(10L);
        entity.setAgentUserId(200L);
        return entity;
    }
}
