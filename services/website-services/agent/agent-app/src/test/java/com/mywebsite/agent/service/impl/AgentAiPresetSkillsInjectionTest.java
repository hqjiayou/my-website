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
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.entity.AgentAiChainPresetEntity;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.mapper.AgentAiChainPresetMapper;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentMcpSelectionService;
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
 * 预设默认集下发（Issue #211 UoW-9）：组装快照的 presetSkills 原样进入正式对话
 * 请求的 skills 字段；无预设时 skills 为 null，报文与旧版一致（Python env 兜底）。
 */
@ExtendWith(MockitoExtension.class)
class AgentAiPresetSkillsInjectionTest {

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
                Mockito.mock(AgentExperienceLessonService.class),
                Mockito.mock(AgentMcpSelectionService.class),
                agentMapper,
                chainPresetMapper,
                Mockito.mock(KnowledgeSpaceResolver.class));
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        // #209 合并适配：知识片段改空间集口径后，本测试的解析器替身回空集、文档查询不再触发，此 stub 改 lenient。
        org.mockito.Mockito.lenient()
                .when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());
        when(memoryService.recentContents(10L, 200L)).thenReturn(List.of());
        // 同 AgentAiModelChainTest 口径：解密放宽为剥 "enc:" 前缀，否则入口被过滤、链为空。
        org.mockito.Mockito.lenient()
                .when(modelEntryCipher.decrypt(any()))
                .thenAnswer(invocation -> {
                    String raw = invocation.getArgument(0);
                    return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
                });
        AgentAiModelEntryEntity globalDefault = new AgentAiModelEntryEntity();
        globalDefault.setId(1L);
        globalDefault.setModelSpec("google:global-default");
        globalDefault.setApiKey("enc:key-1");
        globalDefault.setEnabled(true);
        globalDefault.setDefaultEntry(true);
        // #210 UoW-7 合并适配：组链改吃 agent 引用的对话链预设——agent 10 引用预设 501，
        // 成员只有入口 1（即原全站默认），链与旧两层口径下等价。
        AgentEntity chainAgent = new AgentEntity();
        chainAgent.setId(10L);
        chainAgent.setChainPresetId(501L);
        when(agentMapper.selectById(10L)).thenReturn(chainAgent);
        AgentAiChainPresetEntity chainPreset = new AgentAiChainPresetEntity();
        chainPreset.setId(501L);
        chainPreset.setEntryIds("[1]");
        when(chainPresetMapper.selectById(501L)).thenReturn(chainPreset);
        when(modelEntryMapper.selectBatchIds(any())).thenReturn(List.of(globalDefault));
        when(agentClient.chat(any()))
                .thenReturn(new AgentChatResponse(
                        "reply", "77", "LIVE", List.of(), AgentRunMeta.EMPTY, List.of(), List.of()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void presetSkillsAreSentAsRequestSkills() {
        stubPreparation(List.of("skill-b", "skill-a", "unknown-x"));

        AgentChatRequest request = capturedRequest();

        assertThat(request.skills()).containsExactly("skill-b", "skill-a", "unknown-x");
        assertThat(request.promptProfile()).isEqualTo(new AgentPromptProfile("预设提示", null));
    }

    @Test
    void withoutPresetSkillsRequestSkillsStayNull() {
        stubPreparation(null);

        AgentChatRequest request = capturedRequest();

        assertThat(request.skills()).isNull();
    }

    private void stubPreparation(List<String> presetSkills) {
        AgentAiConversationEntity conversation = new AgentAiConversationEntity();
        conversation.setId(77L);
        conversation.setAgentId(10L);
        conversation.setAgentUserId(200L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(
                        conversation,
                        List.of(),
                        presetSkills == null ? null : new AgentPromptProfile("预设提示", null),
                        List.of(),
                        presetSkills));
    }

    private AgentChatRequest capturedRequest() {
        service.chat(10L, new AgentAiChatRequestDto("hello", null, null, null, "KEY"));
        ArgumentCaptor<AgentChatRequest> captor = ArgumentCaptor.forClass(AgentChatRequest.class);
        verify(agentClient).chat(captor.capture());
        return captor.getValue();
    }
}
