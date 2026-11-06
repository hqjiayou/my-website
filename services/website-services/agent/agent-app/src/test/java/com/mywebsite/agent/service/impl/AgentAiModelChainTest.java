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
import com.mywebsite.agent.entity.AgentEntity;
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
import java.util.ArrayList;
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
 * 站点对话带入的入口生效链：与 admin selectionChain 同规则。链内置化后
 * （Issue #214 UoW-3）链由 agent 自有字段决定：按模式各一份有序 entry ids
 * （V69；迁移时从 #210 的链预设引用逐字拷入，之后与模板脱钩），名单序内
 * 第一个默认作主用，其后按名单序备援；夹具以 {@link #givenOwnChain} 声明
 * 两模式名单（入口未标 mode 按 KEY 归属，与 V35 列默认同口径）。
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

    private AgentAiServiceImpl service;

    /** 当前 agent 10 的夹具实体：改链用例直接改它的两个自有链字段。 */
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
                org.mockito.Mockito.mock(KnowledgeSpaceResolver.class));
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        // 片段回退在无可达空间时短路不查文档（Issue #209 UoW-4），本 stub 只在有空间的用例才被消费。
        lenient().when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        AgentAiConversationEntity conversation = new AgentAiConversationEntity();
        conversation.setId(77L);
        conversation.setAgentId(10L);
        conversation.setAgentUserId(200L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(conversation, List.of(), null, List.of(), null));
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
     * 声明 agent 10 的自有链：按成员入口的 mode 把成员 id 分别写入
     * {@code chainKeyEntryIds} / {@code chainCliEntryIds}（参数顺序即各模式名单序，
     * 入口未标 mode 按 KEY 归属）；{@code selectBatchIds} 默认按参数顺序返回，
     * 乱序口径另由专门用例覆盖。
     */
    private void givenOwnChain(List<AgentAiModelEntryEntity> members) {
        givenOwnChain(members, members);
    }

    private void givenOwnChain(
            List<AgentAiModelEntryEntity> members, List<AgentAiModelEntryEntity> batchResult) {
        List<Long> keyIds = new ArrayList<>();
        List<Long> cliIds = new ArrayList<>();
        for (AgentAiModelEntryEntity member : members) {
            String mode = member.getMode();
            if (mode != null && "CLI".equalsIgnoreCase(mode.trim())) {
                cliIds.add(member.getId());
            } else {
                keyIds.add(member.getId());
            }
        }
        agent.setChainKeyEntryIds(joinIds(keyIds));
        agent.setChainCliEntryIds(joinIds(cliIds));
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenReturn(batchResult);
    }

    private static String joinIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
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
    void chatSendsOwnChainDefaultAsModelAndRestAsFallbacks() {
        givenOwnChain(List.of(
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
    void chatFallsToLaterDefaultWhenOwnChainStartsWithNonDefault() {
        givenOwnChain(List.of(
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
    void chatFollowsOwnChainOrderNotEntryQueryOrder() {
        List<AgentAiModelEntryEntity> members = List.of(
                entry(3L, "google:agent-default", true),
                entry(4L, "openai:agent-alt", false),
                entry(1L, "google:global-default", true),
                entry(2L, "anthropic:global-alt", false));
        // 入口批量查询的返回序与名单序无关：组链必须按名单序重排
        givenOwnChain(members, List.of(members.get(3), members.get(1), members.get(2), members.get(0)));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt", "google:global-default", "anthropic:global-alt");
    }

    @Test
    void rewritingOwnChainContentsSwapsTheChain() {
        AgentAiModelEntryEntity agentDefault = entry(3L, "google:agent-default", true);
        AgentAiModelEntryEntity agentAlt = entry(4L, "openai:agent-alt", false);
        AgentAiModelEntryEntity globalDefault = entry(1L, "google:global-default", true);
        AgentAiModelEntryEntity globalAlt = entry(2L, "anthropic:global-alt", false);
        List<AgentAiModelEntryEntity> all =
                List.of(agentDefault, agentAlt, globalDefault, globalAlt);
        // 批量查询都回全集，组链只取当前自有链名单的成员并按其序
        givenOwnChain(List.of(agentDefault, agentAlt), all);

        AgentChatRequest first = capturedRequest();
        assertThat(first.model().spec()).isEqualTo("google:agent-default");
        assertThat(first.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt");

        givenOwnChain(List.of(globalDefault, globalAlt), all);
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
    void chatBlockedWhenAgentHasNoOwnChain() {
        // 两模式自有链皆空（agent 夹具默认即如此）＝无链：不许进程模型顶上，
        // 直接 BLOCKED（Issue #145 F1 同口径）
        assertThat(agent.getChainKeyEntryIds()).isNull();
        assertThat(agent.getChainCliEntryIds()).isNull();
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
        givenOwnChain(List.of(
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
        givenOwnChain(List.of(
                entry(3L, "google:agent-key-default", true, "KEY"),
                entry(5L, "openai:agent-cli-default", true, "CLI"),
                entry(6L, "anthropic:agent-cli-alt", false, "CLI"),
                entry(1L, "google:global-key-default", true, "KEY"),
                entry(7L, "openai:global-cli-alt", false, "CLI")));

        AgentChatRequest request = capturedRequest("CLI");

        // 备援封闭在 CLI 模式内：只读 CLI 自有链，KEY 名单一概不入链
        assertThat(request.model().spec()).isEqualTo("openai:agent-cli-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("anthropic:agent-cli-alt", "openai:global-cli-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatWithCliModeButNoCliDefaultBlocked() {
        givenOwnChain(List.of(
                entry(3L, "google:agent-key-default", true, "KEY"),
                entry(1L, "google:global-key-default", true, "KEY")));

        // CLI 自有链为空：不跨模式顶 KEY 主用，也不许进程模型顶上（Issue #145 F1）
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
        givenOwnChain(List.of(entry(3L, "google:gemini-key", true, "KEY")));

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
        givenOwnChain(List.of(
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
        givenOwnChain(List.of(entry(3L, "google:agent-default", true)));

        // 点名范围限链内：不在该 agent 自有链成员里的入口一律拒绝，不改走默认
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
        givenOwnChain(List.of(entry(3L, "google:agent-default", true), broken));

        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY", 4L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(com.mywebsite.common.enums.ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode());
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void modelModesReportsOwnChainWithOnlyPlatformDefault() {
        givenOwnChain(List.of(entry(1L, "google:global-default", true, "KEY")));

        var modes = service.modelModes(10L);

        assertThat(modes.modes().get(0).available()).isTrue();
        assertThat(modes.modes().get(0).entries()).extracting(item -> item.id()).containsExactly(1L);
        assertThat(modes.modes().get(0).entries().get(0).modelSpec()).isEqualTo("google:global-default");
    }

    @Test
    void chatSkipsChainMemberMissingFromBatchResult() {
        AgentAiModelEntryEntity primary = entry(3L, "google:agent-default", true);
        AgentAiModelEntryEntity vanished = entry(5L, "openai:vanished", false);
        AgentAiModelEntryEntity alt = entry(7L, "anthropic:agent-alt", false);
        // 自有链声明三名成员，批量查询少返中间一名（成员已删）：应被跳过，链由剩余成员组成。
        givenOwnChain(List.of(primary, vanished, alt), List.of(primary, alt));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("anthropic:agent-alt");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatBlockedWhenOwnChainEntryIdsMalformed() {
        agent.setChainKeyEntryIds("[{broken");

        // 自有链 entry_ids 脏 JSON 按无成员处理 → 空链 BLOCKED，不把解析异常抛给调用方。
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void chatBlockedWhenAllChainMembersMissingFromBatchResult() {
        agent.setChainKeyEntryIds("[3, 4]");
        lenient().when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of());

        // 名单成员全部查无此行（全部已删）→ 空链 BLOCKED，不回退任何默认链。
        assertThatThrownBy(() -> service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(com.mywebsite.common.enums.ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
        verify(agentClient, never()).chat(any());
    }

    @Test
    void chatDedupesDuplicateOwnChainMemberIdsKeepingOrder() {
        AgentAiModelEntryEntity primary = entry(3L, "google:agent-default", true);
        AgentAiModelEntryEntity alt = entry(5L, "openai:agent-alt", false);
        // 自有链含重复 id（[3,3,5]）：去重保序，链中同一入口只出现一次。
        givenOwnChain(List.of(primary, primary, alt), List.of(primary, alt));

        AgentChatRequest request = capturedRequest();

        assertThat(request.model().spec()).isEqualTo("google:agent-default");
        assertThat(request.fallbackModels())
                .extracting(selection -> selection.spec())
                .containsExactly("openai:agent-alt");
    }
}
