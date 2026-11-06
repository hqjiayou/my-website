package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentKindUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiPromptTemplateEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper;
import com.mywebsite.admin.mapper.AdminAiPromptTemplateMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.agent.mapper.AgentAiConversationShareMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserMemoryMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 列表关联摘要与类型专属改写（Issue #213 UoW-A；Prompt 摘要 #214 UoW-4 改）：
 * listSites 组装订阅数（订阅表行数，含创建者行）、自有链两模式成员数与 Prompt
 * 本地副本摘要（来源模板名批量预载、解析不到 name=null 但 id 照实；已配置标记
 * 与自有技能数由 agent 行字段计得）；updateKind 走封闭枚举＋乐观锁（口径同
 * status）。基础信息更新不再触碰 Prompt 副本（专属端点在 AdminAiPromptTemplateService）。
 */
@ExtendWith(MockitoExtension.class)
class AdminAgentServiceImplAssociationsTest {

    @Mock private AdminAgentMapper adminAgentMapper;
    @Mock private AdminAppearanceService adminAppearanceService;
    @Mock private AdminMediaService adminMediaService;
    @Mock private AdminContentService adminContentService;
    @Mock private AdminAiKnowledgeService adminAiKnowledgeService;
    @Mock private AdminAiConversationService adminAiConversationService;
    @Mock private AgentUserMapper agentUserMapper;
    @Mock private AgentUserMemoryMapper agentUserMemoryMapper;
    @Mock private AgentAiConversationShareMapper conversationShareMapper;
    @Mock private AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper;
    @Mock private AdminAiPromptTemplateMapper promptTemplateMapper;

    private AdminAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAgentServiceImpl(
                adminAgentMapper,
                adminAppearanceService,
                adminMediaService,
                adminContentService,
                adminAiKnowledgeService,
                adminAiConversationService,
                agentUserMapper,
                agentUserMemoryMapper,
                conversationShareMapper,
                subscriptionMapper,
                promptTemplateMapper,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private static AdminAgentEntity agent(
            long id, String kind, String chainKeyEntryIds, String chainCliEntryIds, Long promptTemplateId) {
        AdminAgentEntity entity = new AdminAgentEntity();
        entity.setId(id);
        entity.setAgentCode("agent_" + id);
        entity.setAgentName("Agent " + id);
        entity.setAgentType("PERSONAL");
        entity.setAgentKind(kind);
        entity.setStatus("ACTIVE");
        entity.setChainKeyEntryIds(chainKeyEntryIds);
        entity.setChainCliEntryIds(chainCliEntryIds);
        entity.setPromptTemplateId(promptTemplateId);
        entity.setVersion(0);
        return entity;
    }

    @Test
    void listSitesAssemblesSubscriptionCountsChainCountsAndPromptSummary() {
        AdminAgentEntity withAll = agent(1L, "AI_ONLY", "[11, 12]", "[13]", 21L);
        withAll.setPromptSystemPrompt("本地提示");
        withAll.setDefaultSkillIds("[\"s1\",\"s2\"]");
        AdminAgentEntity bare = agent(2L, "SITE", null, null, null);
        Page<AdminAgentEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(withAll, bare));
        page.setTotal(2);
        when(adminAgentMapper.selectPage(any(), any())).thenReturn(page);
        // agent 1 三行订阅（含一行创建者行，口径只数行数）、agent 2 一行（聚合查询结果行）。
        when(subscriptionMapper.selectMaps(any()))
                .thenReturn(List.of(
                        Map.of("agent_id", 1L, "subscription_count", 3L),
                        Map.of("agent_id", 2L, "subscription_count", 1L)));
        AdminAiPromptTemplateEntity template = new AdminAiPromptTemplateEntity();
        template.setId(21L);
        template.setName("严谨口径");
        when(promptTemplateMapper.selectBatchIds(anyCollection())).thenReturn(List.of(template));

        PageResult<AdminAgentListItemDto> result = service.listSites(new AdminAgentListQuery(1, 20, null, null));

        assertThat(result.getRecords()).hasSize(2);
        AdminAgentListItemDto first = result.getRecords().get(0);
        assertThat(first.agentKind()).isEqualTo("AI_ONLY");
        assertThat(first.subscriptionCount()).isEqualTo(3L);
        // 自有链摘要（#214 UoW-3）：两模式成员数直接由 agent 行的链字段计得
        assertThat(first.chainKeyCount()).isEqualTo(2);
        assertThat(first.chainCliCount()).isEqualTo(1);
        // Prompt 摘要（#214 UoW-4）：留痕 id＋模板名、已配置标记、自有技能数同由行字段计得
        assertThat(first.promptTemplateId()).isEqualTo(21L);
        assertThat(first.promptTemplateName()).isEqualTo("严谨口径");
        assertThat(first.promptConfigured()).isTrue();
        assertThat(first.defaultSkillCount()).isEqualTo(2);
        AdminAgentListItemDto second = result.getRecords().get(1);
        assertThat(second.agentKind()).isEqualTo("SITE");
        assertThat(second.subscriptionCount()).isEqualTo(1L);
        assertThat(second.chainKeyCount()).isZero();
        assertThat(second.chainCliCount()).isZero();
        assertThat(second.promptTemplateId()).isNull();
        assertThat(second.promptTemplateName()).isNull();
        assertThat(second.promptConfigured()).isFalse();
        assertThat(second.defaultSkillCount()).isZero();
    }

    @Test
    void listSitesKeepsTemplateTraceButNullNameWhenTemplateNoLongerResolves() {
        // 来源模板已被删：批量预载查不到行，name 落 null，但留痕 id 照实回；
        // 只有留痕也算已配置（与解析的非 null profile 口径一致）。
        AdminAgentEntity stale = agent(3L, "SITE", null, null, 98L);
        Page<AdminAgentEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(stale));
        page.setTotal(1);
        when(adminAgentMapper.selectPage(any(), any())).thenReturn(page);
        when(subscriptionMapper.selectMaps(any())).thenReturn(List.of());
        when(promptTemplateMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        PageResult<AdminAgentListItemDto> result = service.listSites(new AdminAgentListQuery(1, 20, null, null));

        AdminAgentListItemDto item = result.getRecords().get(0);
        assertThat(item.subscriptionCount()).isZero();
        assertThat(item.promptTemplateId()).isEqualTo(98L);
        assertThat(item.promptTemplateName()).isNull();
        assertThat(item.promptConfigured()).isTrue();
    }

    @Test
    void updateKindSwitchesKindAndReturnsDetail() {
        AdminAgentEntity entity = agent(5L, "SITE", null, null, null);
        when(adminAgentMapper.selectById(5L)).thenReturn(entity);
        when(adminAgentMapper.updateById(entity)).thenReturn(1);

        AdminAgentDetailDto detail = service.updateKind(5L, new AdminAgentKindUpdateRequest("AI_ONLY", 0));

        assertThat(detail.agentKind()).isEqualTo("AI_ONLY");
        assertThat(detail.id()).isEqualTo(5L);
    }

    @Test
    void updateKindRejectsValueOutsideClosedEnum() {
        when(adminAgentMapper.selectById(5L)).thenReturn(agent(5L, "SITE", null, null, null));

        assertThatThrownBy(() -> service.updateKind(5L, new AdminAgentKindUpdateRequest("HYBRID", 0)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
                            assertThat(ex.getMessageKey()).isEqualTo(AdminAgentMessageKeys.AGENT_KIND_INVALID);
                        });
        verify(adminAgentMapper, never()).updateById(any(AdminAgentEntity.class));
    }

    @Test
    void updateKindReturnsNotFoundForMissingAgentEvenWithInvalidKind() {
        // 校验顺序：先验存在再验枚举——agent 不存在时即使 kind 非法也先 404。
        when(adminAgentMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateKind(404L, new AdminAgentKindUpdateRequest("HYBRID", 0)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(adminAgentMapper, never()).updateById(any(AdminAgentEntity.class));
    }

    @Test
    void updateSiteLeavesPromptCopyUntouched() {
        // 基础信息全量更新不带 Prompt 字段（#214 UoW-4）：本地副本、留痕与自有
        // 技能集原样保留，详情只回留痕 id。
        AdminAgentEntity entity = agent(6L, "SITE", null, null, 21L);
        entity.setPromptSystemPrompt("本地提示");
        entity.setDefaultSkillIds("[\"s1\"]");
        when(adminAgentMapper.selectById(6L)).thenReturn(entity);
        when(adminAgentMapper.updateById(entity)).thenReturn(1);

        AdminAgentDetailDto detail =
                service.updateSite(6L, new AdminAgentUpdateRequest("Renamed", null, null, null, 0, null));

        assertThat(detail.promptTemplateId()).isEqualTo(21L);
        assertThat(entity.getPromptSystemPrompt()).isEqualTo("本地提示");
        assertThat(entity.getDefaultSkillIds()).isEqualTo("[\"s1\"]");
    }

    @Test
    void listSitesClampsPageNumToAtLeastOne() {
        Page<AdminAgentEntity> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(adminAgentMapper.selectPage(any(), any())).thenReturn(page);

        service.listSites(new AdminAgentListQuery(0, 20, null, null));

        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(adminAgentMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1L);
    }

    @Test
    void updateKindSurfacesOptimisticLockConflict() {
        AdminAgentEntity entity = agent(5L, "SITE", null, null, null);
        when(adminAgentMapper.selectById(5L)).thenReturn(entity);
        when(adminAgentMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service.updateKind(5L, new AdminAgentKindUpdateRequest("AI_ONLY", 3)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
                            assertThat(ex.getMessageKey())
                                    .isEqualTo(AdminAgentMessageKeys.AGENT_VERSION_CONFLICT);
                        });
    }

    @Test
    void createSiteDefaultsKindToSiteAndAcceptsExplicitKind() {
        when(adminAgentMapper.selectOne(any())).thenReturn(null);
        when(adminAgentMapper.insert(any(AdminAgentEntity.class))).thenAnswer(invocation -> {
            AdminAgentEntity inserted = invocation.getArgument(0);
            inserted.setId(9L);
            return 1;
        });
        when(adminAgentMapper.selectById(any())).thenAnswer(invocation -> {
            AdminAgentEntity found = new AdminAgentEntity();
            found.setId(9L);
            found.setAgentKind("SITE");
            return found;
        });

        service.createSite(new AdminAgentCreateRequest("kind_a", "A", "PERSONAL", null, null, null));
        ArgumentCaptor<AdminAgentEntity> captor = ArgumentCaptor.forClass(AdminAgentEntity.class);
        verify(adminAgentMapper).insert(captor.capture());
        assertThat(captor.getValue().getAgentKind()).isEqualTo("SITE");

        service.createSite(
                new AdminAgentCreateRequest("kind_b", "B", "PERSONAL", null, null, null, "AI_ONLY"));
        ArgumentCaptor<AdminAgentEntity> captor2 = ArgumentCaptor.forClass(AdminAgentEntity.class);
        verify(adminAgentMapper, org.mockito.Mockito.times(2)).insert(captor2.capture());
        assertThat(captor2.getValue().getAgentKind()).isEqualTo("AI_ONLY");
    }

    @Test
    void createSiteRejectsInvalidKind() {
        when(adminAgentMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.createSite(
                        new AdminAgentCreateRequest("kind_c", "C", "PERSONAL", null, null, null, "HYBRID")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
        verify(adminAgentMapper, never()).insert(any(AdminAgentEntity.class));
    }
}
