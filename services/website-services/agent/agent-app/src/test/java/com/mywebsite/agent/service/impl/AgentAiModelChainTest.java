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
import com.mywebsite.agent.entity.AgentAiChainPresetEntity;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.mapper.AgentAiChainPresetMapper;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentMcpSelectionService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 站点对话带入的入口生效链：与 admin selectionChain 同规则。链预设落地后
 * （Issue #210 UoW-7）链由 agent 引用的对话链预设决定：按预设的有序成员取
 * 启用入口，预设序内第一个默认作主用，其后按预设序备援；夹具以
 * {@link #givenChainPreset} 声明预设成员（顺序即预设序，与 V63 按旧链生成的
 * 预设同形态：原专属入口在前、平台入口在后）。
 */
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

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentAiChainPresetMapper chainPresetMapper;

    private AgentAiServiceImpl service;

    /** 当前 agent 10 的夹具实体：换预设用例直接改它的 chainPresetId。 */
    private AgentEntity agent;

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
                org.mockito.Mockito.mock(AgentExperienceLessonService.class),
                org.mockito.Mockito.mock(AgentMcpSelectionService.class),
                agentMapper,
                chainPresetMapper);
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
        agent = new AgentEntity();
        agent.setId(10L);
        lenient().when(agentMapper.selectById(10L)).thenReturn(agent);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static AgentAiModelEntryEntity entry(long id, String spec, boolean isDefault) {
        AgentAiModelEntryEntity entry = new AgentAiModelEntryEntity();
        entry.setId(id);
        entry.setModelSpec(spec);
        entry.setApiKey("enc:key-" + id);
        entry.setEnabled(true);
        entry.setDefaultEntry(isDefault);
        return entry;
    }

    private static AgentAiModelEntryEntity entry(long id, String spec, boolean isDefault, String mode) {
        AgentAiModelEntryEntity entry = entry(id, spec, isDefault);
        entry.setMode(mode);
        return entry;
    }

    /**
     * 让 agent 10 引用一条成员为 {@code members} 的预设（参数顺序即预设序）；
     * {@code selectBatchIds} 默认按参数顺序返回，乱序口径另由专门用例覆盖。
     */
    private void givenChainPreset(List<AgentAiModelEntryEntity> members) {
        givenChainPreset(900L, members, members);
    }

    private void givenChainPreset(
            long presetId,
            List<AgentAiModelEntryEntity> members,
            List<AgentAiModelEntryEntity> batchResult) {
        agent.setChainPresetId(presetId);
        AgentAiChainPresetEntity preset = new AgentAiChainPresetEntity();
        preset.setId(presetId);
        preset.setEntryIds(members.stream()
                .map(entry -> String.valueOf(entry.getId()))
                .collect(Collectors.joining(",", "[", "]")));
        when(chainPresetMapper.selectById(presetId)).thenReturn(preset);
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenReturn(batchResult);
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
    void chatSendsPresetDefaultAsModelAndRestAsFallbacks() {
        givenChainPreset(List.of(
                entry(3L, "google:agent-default", true),
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true),
                entry(2L, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.model().apiKey()).isEqualTo("key-3");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "google:global-default", "anthropic:global-alt");
    }

    @Test
    void chatFallsToLaterDefaultWhenPresetStartsWithNonDefault() {
        givenChainPreset(List.of(
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true),
                entry(2L, "anthropic:global-alt", false)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:global-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "anthropic:global-alt");
    }

    @Test
    void chatFollowsPresetOrderNotEntryQueryOrder() {
        List<AgentAiModelEntryEntity> members = List.of(
                entry(3L, "google:agent-default", true),
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true),
                entry(2L, "anthropic:global-alt", false));
        // 入口批量查询的返回序与预设序无关：组链必须按预设序重排
        givenChainPreset(
                900L,
                members,
                List.of(members.get(3), members.get(1), members.get(2), members.get(0)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "google:global-default", "anthropic:global-alt");
    }

    @Test
    void swappingPresetReferenceSwapsTheChain() {
        AgentAiModelEntryEntity agentDefault = entry(3L, "google:agent-default", true);
        AgentAiModelEntryEntity agentAlt = entry(4L, "openai:agent-alt", false);
        AgentAiModelEntryEntity globalDefault = entry(1L, "google:global-default", true);
        AgentAiModelEntryEntity globalAlt = entry(2L, "anthropic:global-alt", false);
        givenChainPreset(900L, List.of(agentDefault, agentAlt), List.of(agentDefault, agentAlt));
        givenChainPreset(901L, List.of(globalDefault, globalAlt), List.of(globalDefault, globalAlt));
        // 两条预设的成员批量查询都回全集，组链只取当前引用预设的成员并按其序
        when(modelEntryMapper.selectBatchIds(any()))
                .thenReturn(List.of(agentDefault, agentAlt, globalDefault, globalAlt));

        agent.setChainPresetId(900L);
        AgentChatRequest first = capturedRequest();
        assertThat(first.model().spec()).isEqualTo("google:agent-default");
        assertThat(first.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt");

        agent.setChainPresetId(901L);
        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY"));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient, org.mockito.Mockito.times(2)).chat(captor.capture());
        AgentChatRequest second = captor.getAllValues().get(1);
        assertThat(second.model().spec()).isEqualTo("google:global-default");
        assertThat(second.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("anthropic:global-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatBlockedWhenAgentHasNoChainPreset() {
        agent.setChainPresetId(null);

        // 未引用预设＝无链：不许进程模型顶上，直接 BLOCKED（Issue #145 F1 同口径）
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatBlockedWhenPrimaryKeyUndecryptable() {
        when(modelEntryCipher.decrypt("enc:key-3")).thenReturn("");
        givenChainPreset(List.of(
                entry(3L, "google:agent-default", true),
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true)));

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
        givenChainPreset(List.of(
                entry(3L, "google:agent-key-default", true, "KEY"),
                entry(5L, "openai:agent-cli-default", true, "CLI"),
                entry(6L, "anthropic:agent-cli-alt", false, "CLI"),
                entry(1L, "google:global-key-default", true, "KEY"),
                entry(7L, "openai:global-cli-alt", false, "CLI")));

        AgentChatRequest request = capturedRequest("CLI");

        // 备援封闭在 CLI 模式内：KEY 入口（含预设内 KEY 默认）一概不入链
        assertThat(request.model().spec()).isEqualTo("openai:agent-cli-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("anthropic:agent-cli-alt", "openai:global-cli-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatWithCliModeButNoCliDefaultBlocked() {
        givenChainPreset(List.of(
                entry(3L, "google:agent-key-default", true, "KEY"),
                entry(1L, "google:global-key-default", true, "KEY")));

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
        givenChainPreset(List.of(entry(3L, "google:gemini-key", true, "KEY")));

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
        givenChainPreset(List.of(
                entry(3L, "google:agent-default", true),
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true)));

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
        givenChainPreset(List.of(entry(3L, "google:agent-default", true)));

        // 点名范围限预设内：不在该 agent 预设成员里的入口一律拒绝，不改走默认
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatRejectsNamedEntryWhoseKeyCannotBeDecrypted() {
        AgentAiModelEntryEntity broken = entry(4L, "openai:agent-alt", false);
        broken.setApiKey("");
        givenChainPreset(List.of(entry(3L, "google:agent-default", true), broken));

        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 4L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void modelModesReportsPresetWithOnlyPlatformDefault() {
        givenChainPreset(List.of(entry(1L, "google:global-default", true, "KEY")));

        var modes = service.modelModes(10L);

        assertThat(modes.modes().get(0).available()).isTrue();
        assertThat(modes.modes().get(0).entries()).extracting(item -> item.id()).containsExactly(1L);
        assertThat(modes.modes().get(0).entries().get(0).modelSpec()).isEqualTo("google:global-default");
    }
}
