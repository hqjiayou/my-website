package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.agent.entity.AgentAiConversationShareEntity;
import com.mywebsite.agent.entity.AgentUserEntity;
import com.mywebsite.agent.entity.AgentUserMemoryEntity;
import com.mywebsite.agent.mapper.AgentAiConversationShareMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserMemoryMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 删站级联完整性（#141 UoW-2）：deleteSite 须覆盖全部十路软删——原六路领域服务、
 * 模型入口服务，以及 agent-core 三张表（成员／用户记忆／会话分享）逐行软删。
 * 端到端逐表断言见 AdminAgentManagementMvcTest.deleteSiteCascadesToModelEntriesUsersMemoriesAndShares。
 */
@ExtendWith(MockitoExtension.class)
class AdminAgentServiceImplDeleteCascadeTest {

    @Mock private AdminAgentMapper adminAgentMapper;
    @Mock private AdminAppearanceService adminAppearanceService;
    @Mock private AdminMediaService adminMediaService;
    @Mock private AdminContentService adminContentService;
    @Mock private AdminAiPromptService adminAiPromptService;
    @Mock private AdminAiKnowledgeService adminAiKnowledgeService;
    @Mock private AdminAiConversationService adminAiConversationService;
    @Mock private AdminAiModelEntryService adminAiModelEntryService;
    @Mock private AgentUserMapper agentUserMapper;
    @Mock private AgentUserMemoryMapper agentUserMemoryMapper;
    @Mock private AgentAiConversationShareMapper conversationShareMapper;

    private AdminAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAgentServiceImpl(
                adminAgentMapper,
                adminAppearanceService,
                adminMediaService,
                adminContentService,
                adminAiPromptService,
                adminAiKnowledgeService,
                adminAiConversationService,
                adminAiModelEntryService,
                agentUserMapper,
                agentUserMemoryMapper,
                conversationShareMapper);
    }

    @Test
    void deleteSiteCascadesAcrossAllTenPaths() {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(7L);
        when(adminAgentMapper.selectById(7L)).thenReturn(agent);
        when(adminAgentMapper.deleteById(7L)).thenReturn(1);

        AgentUserEntity member = new AgentUserEntity();
        member.setId(71L);
        member.setAgentId(7L);
        when(agentUserMapper.selectList(any())).thenReturn(List.of(member));
        AgentUserMemoryEntity memory = new AgentUserMemoryEntity();
        memory.setId(72L);
        memory.setAgentId(7L);
        when(agentUserMemoryMapper.selectList(any())).thenReturn(List.of(memory));
        AgentAiConversationShareEntity share = new AgentAiConversationShareEntity();
        share.setId(73L);
        share.setAgentId(7L);
        when(conversationShareMapper.selectList(any())).thenReturn(List.of(share));

        service.deleteSite(7L);

        verify(adminAppearanceService).softDeleteByAgentId(7L);
        verify(adminContentService).softDeleteByAgentId(7L);
        verify(adminMediaService).softDeleteByAgentId(7L);
        verify(adminAiPromptService).softDeleteByAgentId(7L);
        verify(adminAiKnowledgeService).softDeleteByAgentId(7L);
        verify(adminAiConversationService).softDeleteByAgentId(7L);
        verify(adminAiModelEntryService).softDeleteByAgentId(7L);

        assertThat(member.getDeletedAt()).isNotNull();
        assertThat(memory.getDeletedAt()).isNotNull();
        assertThat(share.getDeletedAt()).isNotNull();
        verify(agentUserMapper).updateById(member);
        verify(agentUserMapper).deleteById(71L);
        verify(agentUserMemoryMapper).updateById(memory);
        verify(agentUserMemoryMapper).deleteById(72L);
        verify(conversationShareMapper).updateById(share);
        verify(conversationShareMapper).deleteById(73L);

        assertThat(agent.getDeletedAt()).isNotNull();
        verify(adminAgentMapper).updateById(agent);
        verify(adminAgentMapper).deleteById(7L);
    }
}
