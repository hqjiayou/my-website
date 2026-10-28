package com.mywebsite.site.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
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
import com.mywebsite.site.entity.SiteAiModelEntryEntity;
import com.mywebsite.site.mapper.SiteAiKnowledgeDocumentMapper;
import com.mywebsite.site.mapper.SiteAiModelEntryMapper;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.service.SiteAiRateLimitService;
import com.mywebsite.site.service.SiteUserMemoryService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 站点对话带入的入口生效链：与 admin selectionChain 同规则（站预设主用，其后同站其他、全站其余）。 */
@ExtendWith(MockitoExtension.class)
class SiteAiModelChainTest {

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
        String subject = SubjectCodes.formatSiteUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.SITE, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        SiteAiConversationEntity conversation = new SiteAiConversationEntity();
        conversation.setId(77L);
        conversation.setSiteId(10L);
        conversation.setSiteUserId(200L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(conversation, List.of(), null, List.of()));
        when(memoryService.recentContents(10L, 200L)).thenReturn(List.of());
        // 主用解密失败的用例里通用 stub 会被专用 stub 盖住，放宽避免 strict stub 误报
        lenient()
                .when(modelEntryCipher.decrypt(any()))
                .thenAnswer(invocation -> {
                    String raw = invocation.getArgument(0);
                    return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
                });
        when(agentClient.chat(any()))
                .thenReturn(new AgentChatResponse(
                        "reply", "77", "LIVE", List.of(), AgentRunMeta.EMPTY, List.of(), List.of()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static SiteAiModelEntryEntity entry(long id, Long siteId, String spec, boolean isDefault) {
        SiteAiModelEntryEntity entry = new SiteAiModelEntryEntity();
        entry.setId(id);
        entry.setSiteId(siteId);
        entry.setModelSpec(spec);
        entry.setApiKey("enc:key-" + id);
        entry.setEnabled(true);
        entry.setDefaultEntry(isDefault);
        return entry;
    }

    private AgentChatRequest capturedRequest() {
        service.chat(10L, new SiteAiChatRequestDto("hello", null, null));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        return captor.getValue();
    }

    @Test
    void chatSendsSiteDefaultAsModelAndRestAsFallbacks() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:site-default", true),
                                entry(4L, 10L, "openai:site-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:site-default");
        assertThat(request.model().apiKey()).isEqualTo("key-3");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:site-alt", "google:global-default", "anthropic:global-alt");
    }

    @Test
    void chatFallsToGlobalDefaultWhenSiteHasNoDefault() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(4L, 10L, "openai:site-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:global-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:site-alt", "anthropic:global-alt");
    }

    @Test
    void chatSendsNoModelWhenPrimaryKeyUndecryptable() {
        when(modelEntryCipher.decrypt("enc:key-3")).thenReturn("");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:site-default", true),
                                entry(4L, 10L, "openai:site-alt", false)),
                        List.of(entry(1L, null, "google:global-default", true)));

        AgentChatRequest request = capturedRequest();

        // 主用坏掉时备援不得顶上：整链不带，Python 回退进程模型
        assertThat(request.model()).isNull();
        assertThat(request.fallbackModels()).isNull();
    }
}
