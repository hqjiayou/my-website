package com.mywebsite.site.service.impl;

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
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.entity.SiteAiConversationEntity;
import com.mywebsite.site.mapper.SiteAiKnowledgeDocumentMapper;
import com.mywebsite.site.mapper.SiteAiModelEntryMapper;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.service.SiteAiRateLimitService;
import com.mywebsite.site.service.SiteUserMemoryService;
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
class SiteAiMemoryInjectionTest {

    @Mock
    private AgentClient agentClient;

    @Mock
    private AgentProperties agentProperties;

    @Mock
    private ModelEntryCipher modelEntryCipher;

    @Mock
    private SiteAiRateLimitService rateLimitService;

    @Mock
    private SiteAiConversationService conversationService;

    @Mock
    private SiteUserMemoryService memoryService;

    @Mock
    private SiteAiModelEntryMapper modelEntryMapper;

    @Mock
    private SiteAiKnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private LocalizedMessageService localizedMessageService;

    @Mock
    private ExecutorService siteAiStreamExecutor;

    private SiteAiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SiteAiServiceImpl(
                agentClient,
                agentProperties,
                modelEntryCipher,
                rateLimitService,
                conversationService,
                memoryService,
                modelEntryMapper,
                knowledgeDocumentMapper,
                localizedMessageService,
                siteAiStreamExecutor,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void chatInjectsTwentyMemoriesAndStoresExtractedAfterTheReply() {
        String subject = SubjectCodes.formatSiteUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.SITE, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        SiteAiConversationEntity conversation = conversation(77L);
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

        service.chat(10L, new SiteAiChatRequestDto("hello", null, null));

        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        assertThat(captor.getValue().memories()).containsExactlyElementsOf(memories);
        assertThat(captor.getValue().memoryEnabled()).isTrue();
        InOrder order = inOrder(conversationService, memoryService);
        order.verify(conversationService)
                .recordExchange(any(), any(), any(), any(), any(), any(), any());
        order.verify(memoryService)
                .storeExtracted(10L, 200L, 77L, List.of("  likes   tea  "));
    }

    @Test
    void streamDoneStoresExtractedMemoriesWithTheReply() {
        SiteAiConversationEntity conversation = conversation(77L);
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
                org.mockito.Mockito.mock(SseEmitter.class));

        verify(memoryService).storeExtracted(10L, 200L, 77L, List.of("likes tea"));
    }

    private static SiteAiConversationEntity conversation(long id) {
        SiteAiConversationEntity entity = new SiteAiConversationEntity();
        entity.setId(id);
        entity.setSiteId(10L);
        entity.setSiteUserId(200L);
        return entity;
    }
}
