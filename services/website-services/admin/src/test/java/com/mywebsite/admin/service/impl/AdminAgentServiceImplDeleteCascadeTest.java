package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
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
 * 删站级联完整性（#141 UoW-2；#211 UoW-8 与 #210 UoW-6 先后修订）：deleteSite 须覆盖
 * 软删——五路领域服务（外观／内容／媒体／知识／会话），以及 agent-core 三张表
 * （成员／用户记忆／会话分享）逐行软删。两路先后退役：模型入口随入口平台化退役
 * （#210 UoW-6：入口是平台资源、删站不级联，构造器已无该依赖）；Prompt 配置自
 * #211 起退出级联（旧 profile 表停写、配置预设是共享资源不随 agent 删除），本
 * agent 只清 config_preset_id 引用。
 * 端到端逐表断言见 AdminAgentManagementMvcTest 的删站级联用例。
 */
@ExtendWith(MockitoExtension.class)
class AdminAgentServiceImplDeleteCascadeTest {

    @Mock private AdminAgentMapper adminAgentMapper;
    @Mock private AdminAppearanceService adminAppearanceService;
    @Mock private AdminMediaService adminMediaService;
    @Mock private AdminContentService adminContentService;
    @Mock private AdminAiKnowledgeService adminAiKnowledgeService;
    @Mock private AdminAiConversationService adminAiConversationService;
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
                adminAiKnowledgeService,
                adminAiConversationService,
                agentUserMapper,
                agentUserMemoryMapper,
                conversationShareMapper);
    }

    @Test
    void deleteSiteCascadesAcrossAllPathsAndOnlyClearsPresetReference() {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(7L);
        agent.setConfigPresetId(99L);
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
        verify(adminAiKnowledgeService).softDeleteByAgentId(7L);
        verify(adminAiConversationService).softDeleteByAgentId(7L);

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
        // #211：预设本体不删（共享资源），本 agent 的引用被清空。
        assertThat(agent.getConfigPresetId()).isNull();
        verify(adminAgentMapper).updateById(agent);
        verify(adminAgentMapper).deleteById(7L);
    }
}
