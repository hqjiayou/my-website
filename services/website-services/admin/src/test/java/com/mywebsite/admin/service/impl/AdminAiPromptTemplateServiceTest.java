package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateCreateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiPromptTemplateEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiPromptTemplateMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Prompt 模板 CRUD 与 agent Prompt 配置（Issue #214 UoW-4）：模板只带提示词；
 * agent 侧整表保存本地副本＋自有技能集（JSON 落库保序、空集存 NULL）；套用是
 * 拷贝——改模板不影响已套用 agent；另存刷新留痕。
 */
@ExtendWith(MockitoExtension.class)
class AdminAiPromptTemplateServiceTest {

    @Mock
    private AdminAiPromptTemplateMapper promptTemplateMapper;

    @Mock
    private AdminAgentMapper agentMapper;

    private AdminAiPromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiPromptTemplateServiceImpl(promptTemplateMapper, agentMapper, new ObjectMapper());
    }

    @Test
    void createTrimsNameAndBlanksPromptToNull() {
        when(promptTemplateMapper.insert(any(AdminAiPromptTemplateEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiPromptTemplateEntity entity = invocation.getArgument(0);
                    entity.setId(5L);
                    return 1;
                });
        when(promptTemplateMapper.selectById(5L)).thenReturn(template(5L, "模板甲", null, "0.50"));

        AdminAiPromptTemplateDto dto = service.createTemplate(
                new AdminAiPromptTemplateCreateRequest(" 模板甲 ", "  ", new BigDecimal("0.50")));

        ArgumentCaptor<AdminAiPromptTemplateEntity> captor =
                ArgumentCaptor.forClass(AdminAiPromptTemplateEntity.class);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("模板甲");
        assertThat(captor.getValue().getSystemPrompt()).isNull();
        assertThat(dto.temperature()).isEqualByComparingTo("0.50");
    }

    @Test
    void getMissingTemplateIsNotFound() {
        when(promptTemplateMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getTemplate(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void updateAppliesFields() {
        AdminAiPromptTemplateEntity existing = template(7L, "旧名", "旧提示", null);
        existing.setVersion(3);
        when(promptTemplateMapper.selectById(7L)).thenReturn(existing);
        when(promptTemplateMapper.updateById(existing)).thenReturn(1);

        AdminAiPromptTemplateDto dto = service.updateTemplate(
                7L, new AdminAiPromptTemplateUpdateRequest("新名", "新提示", new BigDecimal("1.20"), 3));

        assertThat(existing.getName()).isEqualTo("新名");
        assertThat(existing.getSystemPrompt()).isEqualTo("新提示");
        assertThat(dto.name()).isEqualTo("新名");
    }

    @Test
    void updateWithStaleVersionConflicts() {
        AdminAiPromptTemplateEntity existing = template(7L, "旧名", "旧提示", null);
        when(promptTemplateMapper.selectById(7L)).thenReturn(existing);
        when(promptTemplateMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service.updateTemplate(
                        7L, new AdminAiPromptTemplateUpdateRequest("新名", null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT.getCode());
    }

    @Test
    void softDeleteStampsDeletedAtAndDeletesById() {
        AdminAiPromptTemplateEntity existing = template(8L, "待删", null, null);
        when(promptTemplateMapper.selectById(8L)).thenReturn(existing);

        service.softDeleteTemplate(8L);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(promptTemplateMapper).updateById(existing);
        verify(promptTemplateMapper).deleteById(8L);
    }

    @Test
    void updateAgentPromptConfigStoresSkillsAsOrderedJsonAndReturnsThem() {
        AdminAgentEntity agent = agent(11L);
        when(agentMapper.selectById(11L)).thenReturn(agent);
        when(agentMapper.updateById(agent)).thenReturn(1);

        AdminAgentPromptConfigDto dto = service.updateAgentPromptConfig(
                11L,
                new AdminAgentPromptConfigUpdateRequest(
                        " 本地提示 ", new BigDecimal("0.50"), List.of("b-skill", " a-skill ", "b-skill", "")));

        assertThat(agent.getPromptSystemPrompt()).isEqualTo("本地提示");
        assertThat(agent.getDefaultSkillIds()).isEqualTo("[\"b-skill\",\"a-skill\"]");
        assertThat(dto.defaultSkillIds()).containsExactly("b-skill", "a-skill");
    }

    @Test
    void updateAgentPromptConfigWithEmptyValuesStoresNulls() {
        AdminAgentEntity agent = agent(11L);
        agent.setPromptSystemPrompt("旧提示");
        agent.setDefaultSkillIds("[\"s1\"]");
        when(agentMapper.selectById(11L)).thenReturn(agent);
        when(agentMapper.updateById(agent)).thenReturn(1);

        AdminAgentPromptConfigDto dto = service.updateAgentPromptConfig(
                11L, new AdminAgentPromptConfigUpdateRequest(" ", null, List.of()));

        assertThat(agent.getPromptSystemPrompt()).isNull();
        assertThat(agent.getDefaultSkillIds()).isNull();
        assertThat(dto.defaultSkillIds()).isEmpty();
    }

    @Test
    void loadTemplateCopiesPromptOnlyAndStampsTrace() {
        AdminAgentEntity agent = agent(11L);
        agent.setDefaultSkillIds("[\"own-skill\"]");
        when(agentMapper.selectById(11L)).thenReturn(agent);
        when(promptTemplateMapper.selectById(21L)).thenReturn(template(21L, "口径", "模板提示", "0.70"));
        when(agentMapper.updateById(agent)).thenReturn(1);

        AdminAgentPromptConfigDto dto = service.loadAgentPromptFromTemplate(11L, 21L);

        assertThat(agent.getPromptSystemPrompt()).isEqualTo("模板提示");
        assertThat(agent.getPromptTemperature()).isEqualByComparingTo("0.70");
        assertThat(agent.getPromptTemplateId()).isEqualTo(21L);
        // 套用只拷 prompt 部分：自有技能集原样不动。
        assertThat(agent.getDefaultSkillIds()).isEqualTo("[\"own-skill\"]");
        assertThat(dto.promptTemplateName()).isEqualTo("口径");
    }

    @Test
    void editingTemplateAfterLoadDoesNotChangeAgentCopy() {
        // 拷贝语义钉子（服务层）：套用后改模板，agent 的本地副本逐字不变。
        AdminAgentEntity agent = agent(11L);
        AdminAiPromptTemplateEntity template = template(21L, "口径", "第一版", null);
        when(agentMapper.selectById(11L)).thenReturn(agent);
        when(promptTemplateMapper.selectById(21L)).thenReturn(template);
        when(agentMapper.updateById(agent)).thenReturn(1);
        when(promptTemplateMapper.updateById(template)).thenReturn(1);

        service.loadAgentPromptFromTemplate(11L, 21L);
        service.updateTemplate(21L, new AdminAiPromptTemplateUpdateRequest("口径", "第二版", null, 0));

        assertThat(template.getSystemPrompt()).isEqualTo("第二版");
        assertThat(agent.getPromptSystemPrompt()).isEqualTo("第一版");
        assertThat(service.getAgentPromptConfig(11L).systemPrompt()).isEqualTo("第一版");
    }

    @Test
    void loadMissingTemplateIsNotFound() {
        when(agentMapper.selectById(11L)).thenReturn(agent(11L));
        when(promptTemplateMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.loadAgentPromptFromTemplate(11L, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void saveAsTemplateCopiesLocalPromptAndPointsTraceAtNewTemplate() {
        AdminAgentEntity agent = agent(11L);
        agent.setPromptSystemPrompt("本地提示");
        agent.setPromptTemperature(new BigDecimal("0.30"));
        when(agentMapper.selectById(11L)).thenReturn(agent);
        when(promptTemplateMapper.insert(any(AdminAiPromptTemplateEntity.class)))
                .thenAnswer(invocation -> {
                    AdminAiPromptTemplateEntity entity = invocation.getArgument(0);
                    entity.setId(31L);
                    return 1;
                });
        when(promptTemplateMapper.selectById(31L))
                .thenReturn(template(31L, "另存的口径", "本地提示", "0.30"));

        AdminAiPromptTemplateDto dto = service.saveAgentPromptAsTemplate(11L, " 另存的口径 ");

        ArgumentCaptor<AdminAiPromptTemplateEntity> captor =
                ArgumentCaptor.forClass(AdminAiPromptTemplateEntity.class);
        verify(promptTemplateMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("另存的口径");
        assertThat(captor.getValue().getSystemPrompt()).isEqualTo("本地提示");
        assertThat(agent.getPromptTemplateId()).isEqualTo(31L);
        verify(agentMapper).updateById(agent);
        assertThat(dto.id()).isEqualTo(31L);
    }

    @Test
    void malformedStoredSkillIdsReadAsEmpty() {
        AdminAgentEntity agent = agent(11L);
        agent.setDefaultSkillIds("{not-json");
        when(agentMapper.selectById(11L)).thenReturn(agent);

        assertThat(service.getAgentPromptConfig(11L).defaultSkillIds()).isEmpty();
    }

    @Test
    void missingAgentIsNotFound() {
        when(agentMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.getAgentPromptConfig(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    private static AdminAiPromptTemplateEntity template(Long id, String name, String systemPrompt, String temperature) {
        AdminAiPromptTemplateEntity template = new AdminAiPromptTemplateEntity();
        template.setId(id);
        template.setName(name);
        template.setSystemPrompt(systemPrompt);
        template.setTemperature(temperature == null ? null : new BigDecimal(temperature));
        template.setVersion(0);
        return template;
    }

    private static AdminAgentEntity agent(long id) {
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setId(id);
        agent.setVersion(0);
        return agent;
    }
}
