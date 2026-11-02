package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

/** 站点对话带入的入口生效链：与 admin selectionChain 同规则（站预设主用，其后同站其他、全站其余）。 */
@ExtendWith(MockitoExtension.class)
class AgentAiModelChainTest {

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
                org.mockito.Mockito.mock(AgentEmbeddingClassService.class));
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        AgentAiConversationEntity conversation = new AgentAiConversationEntity();
        conversation.setId(77L);
        conversation.setAgentId(10L);
        conversation.setAgentUserId(200L);
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

    private static AgentAiModelEntryEntity entry(long id, Long agentId, String spec, boolean isDefault) {
        AgentAiModelEntryEntity entry = new AgentAiModelEntryEntity();
        entry.setId(id);
        entry.setAgentId(agentId);
        entry.setModelSpec(spec);
        entry.setApiKey("enc:key-" + id);
        entry.setEnabled(true);
        entry.setDefaultEntry(isDefault);
        return entry;
    }

    private static AgentAiModelEntryEntity entry(
            long id, Long agentId, String spec, boolean isDefault, String mode) {
        AgentAiModelEntryEntity entry = entry(id, agentId, spec, isDefault);
        entry.setMode(mode);
        return entry;
    }

    private AgentChatRequest capturedRequest(String mode) {
        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, mode));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        return captor.getValue();
    }

    private AgentChatRequest capturedRequest() {
        // 模式必填后（Issue #145 F4c）：无 mode 的旧调用已不存在；未标 mode 的入口按 KEY 归属
        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY"));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        return captor.getValue();
    }

    @Test
    void chatSendsAgentDefaultAsModelAndRestAsFallbacks() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.model().apiKey()).isEqualTo("key-3");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "google:global-default", "anthropic:global-alt");
    }

    @Test
    void chatFallsToGlobalDefaultWhenAgentHasNoDefault() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:global-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "anthropic:global-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatBlockedWhenPrimaryKeyUndecryptable() {
        when(modelEntryCipher.decrypt("enc:key-3")).thenReturn("");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(entry(1L, null, "google:global-default", true)));

        // 主用坏掉时备援不得顶上、进程模型更不得顶上：整链为空直接 BLOCKED（Issue #145 F1）
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
        verify(agentClient, org.mockito.Mockito.never()).chat(any());
    }

    @Test
    void chatWithCliModeUsesCliChainOnly() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-key-default", true, "KEY"),
                                entry(5L, 10L, "openai:agent-cli-default", true, "CLI"),
                                entry(6L, 10L, "anthropic:agent-cli-alt", false, "CLI")),
                        List.of(
                                entry(1L, null, "google:global-key-default", true, "KEY"),
                                entry(7L, null, "openai:global-cli-alt", false, "CLI")));

        AgentChatRequest request = capturedRequest("CLI");

        // 备援封闭在 CLI 模式内：KEY 入口（含两级默认）一概不入链
        assertThat(request.model().spec()).isEqualTo("openai:agent-cli-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("anthropic:agent-cli-alt", "openai:global-cli-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatWithCliModeButNoCliDefaultBlocked() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(3L, 10L, "google:agent-key-default", true, "KEY")),
                        List.of(entry(1L, null, "google:global-key-default", true, "KEY")));

        // 模式内无默认：不跨模式顶 KEY 主用，也不许进程模型顶上（Issue #145 F1）
        assertThatThrownBy(() -> capturedRequest("CLI"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
        verify(agentClient, org.mockito.Mockito.never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatWithoutModeRejected() {
        // 模式必填（Issue #145 F4c）：空 mode 不再等于整链混搭
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatWithInvalidModeRejected() {
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "BOGUS")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void modelModesReportsAvailabilityAndLabelPerMode() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(3L, 10L, "google:gemini-key", true, "KEY")),
                        List.of());

        var modes = service.modelModes(10L);

        assertThat(modes.modes()).hasSize(2);
        assertThat(modes.modes().get(0).mode()).isEqualTo("KEY");
        assertThat(modes.modes().get(0).available()).isTrue();
        assertThat(modes.modes().get(0).label()).isEqualTo("gemini-key");
        assertThat(modes.modes().get(1).mode()).isEqualTo("CLI");
        assertThat(modes.modes().get(1).available()).isFalse();
        assertThat(modes.modes().get(1).label()).isNull();
        assertThat(modes.modes().get(0).entries()).hasSize(1);
        assertThat(modes.modes().get(0).entries().get(0).id()).isEqualTo(3L);
        assertThat(modes.modes().get(0).entries().get(0).modelSpec()).isEqualTo("google:gemini-key");
        assertThat(modes.modes().get(1).entries()).isEmpty();
    }

    @Test
    void chatPinsNamedEntryAsPrimaryAndKeepsTheRestAsFallback() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(entry(1L, null, "google:global-default", true)));

        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 4L));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        AgentChatRequest request = captor.getValue();

        assertThat(request.model().spec()).isEqualTo("openai:agent-alt");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("google:agent-default", "google:global-default");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatRejectsNamedEntryThatIsNotOnTheChain() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(List.of(entry(3L, 10L, "google:agent-default", true)), List.of());

        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatRejectsNamedEntryWhoseKeyCannotBeDecrypted() {
        AgentAiModelEntryEntity broken = entry(4L, 10L, "openai:agent-alt", false);
        broken.setApiKey("");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(List.of(entry(3L, 10L, "google:agent-default", true), broken), List.of());

        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 4L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void modelModesIncludesGlobalDefaultWhenTheAgentHasNone() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(List.of(), List.of(entry(1L, null, "google:global-default", true, "KEY")), List.of(), List.of());

        var modes = service.modelModes(10L);

        assertThat(modes.modes().get(0).available()).isTrue();
        assertThat(modes.modes().get(0).entries()).extracting(item -> item.id()).containsExactly(1L);
        assertThat(modes.modes().get(0).entries().get(0).modelSpec()).isEqualTo("google:global-default");
    }
}
