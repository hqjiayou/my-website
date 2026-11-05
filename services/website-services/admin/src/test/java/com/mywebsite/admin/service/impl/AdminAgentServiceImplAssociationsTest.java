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
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentConfigPresetUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentKindUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiChainPresetEntity;
import com.mywebsite.admin.entity.AdminAiConfigPresetEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiChainPresetMapper;
import com.mywebsite.admin.mapper.AdminAiConfigPresetMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 列表关联摘要与类型／预设专属改写（Issue #213 UoW-A）：listSites 组装订阅数（订阅表
 * 行数，含创建者行）与两类预设名（批量预载、解析不到 name=null 但 id 照实）；
 * updateKind 走封闭枚举＋乐观锁（口径同 status）；updateConfigPreset 仿链预设改引用
 * （null 清除、不存在抛 NOT_FOUND）。
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
    @Mock private AdminAiChainPresetMapper chainPresetMapper;
    @Mock private AdminAiConfigPresetMapper configPresetMapper;

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
                chainPresetMapper,
                configPresetMapper);
    }

    private static AdminAgentEntity agent(long id, String kind, Long chainPresetId, Long configPresetId) {
        AdminAgentEntity entity = new AdminAgentEntity();
        entity.setId(id);
        entity.setAgentCode("agent_" + id);
        entity.setAgentName("Agent " + id);
        entity.setAgentType("PERSONAL");
        entity.setAgentKind(kind);
        entity.setStatus("ACTIVE");
        entity.setChainPresetId(chainPresetId);
        entity.setConfigPresetId(configPresetId);
        entity.setVersion(0);
        return entity;
    }

    private static AdminAiKnowledgeBaseSubscriptionEntity subscription(long agentId, long baseId) {
        AdminAiKnowledgeBaseSubscriptionEntity row = new AdminAiKnowledgeBaseSubscriptionEntity();
        row.setAgentId(agentId);
        row.setBaseId(baseId);
        return row;
    }

    @Test
    void listSitesAssemblesSubscriptionCountsAndPresetNames() {
        AdminAgentEntity withAll = agent(1L, "AI_ONLY", 11L, 21L);
        AdminAgentEntity bare = agent(2L, "SITE", null, null);
        Page<AdminAgentEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(withAll, bare));
        page.setTotal(2);
        when(adminAgentMapper.selectPage(any(), any())).thenReturn(page);
        // agent 1 三行订阅（含一行创建者行，口径只数行数）、agent 2 一行。
        when(subscriptionMapper.selectList(any()))
                .thenReturn(List.of(
                        subscription(1L, 101L), subscription(1L, 102L), subscription(1L, 103L),
                        subscription(2L, 201L)));
        AdminAiChainPresetEntity chainPreset = new AdminAiChainPresetEntity();
        chainPreset.setId(11L);
        chainPreset.setName("平台默认链");
        when(chainPresetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(chainPreset));
        AdminAiConfigPresetEntity configPreset = new AdminAiConfigPresetEntity();
        configPreset.setId(21L);
        configPreset.setName("严谨型");
        when(configPresetMapper.selectBatchIds(anyCollection())).thenReturn(List.of(configPreset));

        PageResult<AdminAgentListItemDto> result = service.listSites(new AdminAgentListQuery(1, 20, null, null));

        assertThat(result.getRecords()).hasSize(2);
        AdminAgentListItemDto first = result.getRecords().get(0);
        assertThat(first.agentKind()).isEqualTo("AI_ONLY");
        assertThat(first.subscriptionCount()).isEqualTo(3L);
        assertThat(first.chainPresetId()).isEqualTo(11L);
        assertThat(first.chainPresetName()).isEqualTo("平台默认链");
        assertThat(first.configPresetId()).isEqualTo(21L);
        assertThat(first.configPresetName()).isEqualTo("严谨型");
        AdminAgentListItemDto second = result.getRecords().get(1);
        assertThat(second.agentKind()).isEqualTo("SITE");
        assertThat(second.subscriptionCount()).isEqualTo(1L);
        assertThat(second.chainPresetId()).isNull();
        assertThat(second.chainPresetName()).isNull();
        assertThat(second.configPresetId()).isNull();
        assertThat(second.configPresetName()).isNull();
    }

    @Test
    void listSitesKeepsIdButNullNameWhenPresetNoLongerResolves() {
        // 预设已被删：批量预载查不到行，name 落 null，但 agent 上的引用 id 照实回。
        AdminAgentEntity stale = agent(3L, "SITE", 99L, 98L);
        Page<AdminAgentEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(stale));
        page.setTotal(1);
        when(adminAgentMapper.selectPage(any(), any())).thenReturn(page);
        when(subscriptionMapper.selectList(any())).thenReturn(List.of());
        when(chainPresetMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(configPresetMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        PageResult<AdminAgentListItemDto> result = service.listSites(new AdminAgentListQuery(1, 20, null, null));

        AdminAgentListItemDto item = result.getRecords().get(0);
        assertThat(item.subscriptionCount()).isZero();
        assertThat(item.chainPresetId()).isEqualTo(99L);
        assertThat(item.chainPresetName()).isNull();
        assertThat(item.configPresetId()).isEqualTo(98L);
        assertThat(item.configPresetName()).isNull();
    }

    @Test
    void updateKindSwitchesKindAndReturnsDetail() {
        AdminAgentEntity entity = agent(5L, "SITE", null, null);
        when(adminAgentMapper.selectById(5L)).thenReturn(entity);
        when(adminAgentMapper.updateById(entity)).thenReturn(1);

        AdminAgentDetailDto detail = service.updateKind(5L, new AdminAgentKindUpdateRequest("AI_ONLY", 0));

        assertThat(detail.agentKind()).isEqualTo("AI_ONLY");
        assertThat(detail.id()).isEqualTo(5L);
    }

    @Test
    void updateKindRejectsValueOutsideClosedEnum() {
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
    void updateKindSurfacesOptimisticLockConflict() {
        AdminAgentEntity entity = agent(5L, "SITE", null, null);
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
    void updateConfigPresetSetsReferenceAfterExistenceCheck() {
        AdminAgentEntity entity = agent(6L, "SITE", null, null);
        when(adminAgentMapper.selectById(6L)).thenReturn(entity);
        AdminAiConfigPresetEntity preset = new AdminAiConfigPresetEntity();
        preset.setId(21L);
        when(configPresetMapper.selectById(21L)).thenReturn(preset);
        when(adminAgentMapper.updateById(entity)).thenReturn(1);

        AdminAgentDetailDto detail =
                service.updateConfigPreset(6L, new AdminAgentConfigPresetUpdateRequest(21L));

        assertThat(detail.configPresetId()).isEqualTo(21L);
    }

    @Test
    void updateConfigPresetClearsReferenceOnNull() {
        AdminAgentEntity entity = agent(6L, "SITE", null, 21L);
        when(adminAgentMapper.selectById(6L)).thenReturn(entity);
        when(adminAgentMapper.updateById(entity)).thenReturn(1);

        AdminAgentDetailDto detail =
                service.updateConfigPreset(6L, new AdminAgentConfigPresetUpdateRequest(null));

        assertThat(detail.configPresetId()).isNull();
        verify(configPresetMapper, never()).selectById(any());
    }

    @Test
    void updateConfigPresetRejectsMissingPreset() {
        AdminAgentEntity entity = agent(6L, "SITE", null, null);
        when(adminAgentMapper.selectById(6L)).thenReturn(entity);
        when(configPresetMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateConfigPreset(6L, new AdminAgentConfigPresetUpdateRequest(404L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(adminAgentMapper, never()).updateById(any(AdminAgentEntity.class));
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
