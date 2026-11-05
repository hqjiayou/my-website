package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentKnowledgeSearchRequestDto;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiKnowledgeDocumentEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentMcpSelectionService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.service.LocalizedMessageService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 字面回退与成员浏览的空间集口径（Issue #209 UoW-4）：document.agentId 停写后，
 * 片段回退（knowledgeSnippets）、搜索文本回退（textKnowledgeHits）与成员文档
 * 列表/详情都按 document.baseId ∈ 该 agent 可达空间集（创建∪订阅）过滤。
 * mapper 被 mock：以捕获到的查询包装器断言过滤形状（base_id 在、agent_id 不在、
 * 参数含订阅空间 id），返回行由 stub 给出。
 */
@ExtendWith(MockitoExtension.class)
class AgentAiKnowledgeSpaceFallbackTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""),
                AgentAiKnowledgeDocumentEntity.class);
    }

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
    private AgentEmbeddingClassService embeddingClassService;

    @Mock
    private KnowledgeSpaceResolver knowledgeSpaceResolver;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>> documentWrapperCaptor;

    @Captor
    private ArgumentCaptor<AgentChatRequest> chatRequestCaptor;

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
                mock(AgentUsageService.class),
                embeddingClassService,
                mock(AgentExperienceLessonService.class),
                mock(AgentMcpSelectionService.class),
                knowledgeSpaceResolver);
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        AuthContext.set(new AuthenticatedUser(TokenAudience.AGENT, subject, "u", 10L, Set.of()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void searchTextFallbackHitsDocumentsInSubscribedSpace() {
        when(knowledgeSpaceResolver.reachableBaseIds(10L)).thenReturn(List.of(200L));
        when(agentClient.searchRetrieval(any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED));
        when(knowledgeDocumentMapper.selectList(documentWrapperCaptor.capture()))
                .thenReturn(List.of(document(5L, 200L, "共享手册", "alpha 共享内容")));

        var response = service.searchKnowledge(10L, new AgentKnowledgeSearchRequestDto("alpha", null));

        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).sourceId()).isEqualTo("kb:5");
        assertThat(response.hits().get(0).content()).contains("alpha 共享内容");
        assertBaseSetFilter(documentWrapperCaptor.getValue(), 200L);
    }

    @Test
    void searchTextFallbackSkipsDocumentQueryWhenNoReachableSpace() {
        when(knowledgeSpaceResolver.reachableBaseIds(10L)).thenReturn(List.of());
        when(agentClient.searchRetrieval(any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED));

        var response = service.searchKnowledge(10L, new AgentKnowledgeSearchRequestDto("alpha", null));

        assertThat(response.hits()).isEmpty();
        assertThat(response.degraded()).isTrue();
        verify(knowledgeDocumentMapper, never()).selectList(any());
    }

    @Test
    void chatSnippetsComeFromSubscribedSpaceDocuments() {
        String subject = SubjectCodes.formatAgentUserSubject(200L);
        when(rateLimitService.tryAcquire(10L, subject)).thenReturn(true);
        when(localizedMessageService.currentLanguageTag()).thenReturn("zh-Hant-TW");
        when(knowledgeSpaceResolver.reachableBaseIds(10L)).thenReturn(List.of(200L));
        when(knowledgeDocumentMapper.selectList(documentWrapperCaptor.capture()))
                .thenReturn(List.of(document(7L, 200L, "订阅空间手册", "alpha 正文内容")));
        AgentAiConversationEntity conversation = new AgentAiConversationEntity();
        conversation.setId(77L);
        conversation.setAgentId(10L);
        conversation.setAgentUserId(200L);
        when(conversationService.prepare(10L, 200L, null))
                .thenReturn(new ChatPreparation(conversation, List.of(), null, List.of()));
        when(memoryService.recentContents(10L, 200L)).thenReturn(List.of());
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
        when(agentClient.chat(chatRequestCaptor.capture()))
                .thenReturn(new AgentChatResponse(
                        "reply", "77", "LIVE", List.of(), AgentRunMeta.EMPTY, List.of(), List.of()));

        service.chat(10L, new AgentAiChatRequestDto("alpha 手册问题", null, null, null, "KEY"));

        assertThat(chatRequestCaptor.getValue().knowledgeContext())
                .anyMatch(snippet -> snippet.contains("订阅空间手册") && snippet.contains("alpha 正文内容"));
        assertBaseSetFilter(documentWrapperCaptor.getValue(), 200L);
    }

    @Test
    void memberDocumentListFiltersByReachableSpaces() {
        when(knowledgeSpaceResolver.reachableBaseIds(10L)).thenReturn(List.of(100L, 200L));
        when(knowledgeDocumentMapper.selectList(documentWrapperCaptor.capture())).thenReturn(List.of());

        assertThat(service.listKnowledgeDocuments(10L)).isEmpty();

        assertBaseSetFilter(documentWrapperCaptor.getValue(), 100L, 200L);
    }

    @Test
    void memberDocumentDetailRequiresDocumentInReachableSpace() {
        when(knowledgeSpaceResolver.reachableBaseIds(10L)).thenReturn(List.of(200L));
        when(knowledgeDocumentMapper.selectOne(documentWrapperCaptor.capture()))
                .thenReturn(document(9L, 200L, "共享手册", "# 共享正文"));

        var detail = service.getKnowledgeDocument(10L, 9L);

        assertThat(detail.title()).isEqualTo("共享手册");
        assertThat(detail.content()).isEqualTo("# 共享正文");
        assertBaseSetFilter(documentWrapperCaptor.getValue(), 200L);
    }

    private static AgentAiKnowledgeDocumentEntity document(long id, long baseId, String title, String content) {
        AgentAiKnowledgeDocumentEntity document = new AgentAiKnowledgeDocumentEntity();
        document.setId(id);
        document.setBaseId(baseId);
        document.setTitle(title);
        document.setContent(content);
        document.setStatus(AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        return document;
    }

    /**
     * 过滤形状断言：WHERE 只按 baseId 圈空间集，不得再出现 agentId；参数含全部
     * 空间 id。本单测 harness 的 TableInfo 只登记属性名，片段按属性名渲染
     * （baseId/agentId）；属性→列名的下划线映射由 Spring 上下文的 MyBatis-Plus
     * 全局策略保证，列级实证见 H2 MvcTest（internal 枚举按 base_id 过滤已绿）。
     */
    private static void assertBaseSetFilter(
            LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity> wrapper, Long... baseIds) {
        assertThat(wrapper.getSqlSegment()).contains("baseId").doesNotContain("agentId");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(baseIds);
    }
}
