package com.mywebsite.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.entity.AgentAiConfigPresetEntity;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.mapper.AgentAiConfigPresetMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 配置预设解析（Issue #211 UoW-8）：正式对话组装改走 agent → config_preset_id → 预设。
 * 以组装快照（{@link ChatPreparation#promptProfile()}）对照验收：存量等价、共享生效、
 * 回退三态。agent id 用 91xx 段，与其他测试夹具隔离。
 */
class AgentAiConfigPresetResolutionTest extends AbstractAgentSpringBootTest {

    @Autowired
    private AgentAiConversationService conversationService;

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentAiConfigPresetMapper configPresetMapper;

    @Test
    void migratedAgentResolvesSameProfileAsLegacyProfileRow() {
        // 存量等价快照：旧 profile P（systemPrompt＋temperature、无默认集）经 V57
        // 平移为预设后，正式组装出的 promptProfile 与旧解析逐字一致。
        AgentAiConfigPresetEntity preset = insertPreset("助手一 的配置预设", "你是本站的专属助手。", "0.70", null);
        long agentId = insertAgent(9101, preset.getId());

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.promptProfile())
                .isEqualTo(new AgentPromptProfile("你是本站的专属助手。", 0.7d));
    }

    @Test
    void sharedPresetUpdateTakesEffectForAllReferencingAgents() {
        AgentAiConfigPresetEntity preset = insertPreset("共享预设", "第一版提示", null, null);
        long agentA = insertAgent(9102, preset.getId());
        long agentB = insertAgent(9103, preset.getId());

        assertThat(conversationService.prepare(agentA, 200L, null).promptProfile().systemPrompt())
                .isEqualTo("第一版提示");
        assertThat(conversationService.prepare(agentB, 201L, null).promptProfile().systemPrompt())
                .isEqualTo("第一版提示");

        preset.setSystemPrompt("第二版提示");
        configPresetMapper.updateById(preset);

        assertThat(conversationService.prepare(agentA, 200L, null).promptProfile().systemPrompt())
                .isEqualTo("第二版提示");
        assertThat(conversationService.prepare(agentB, 201L, null).promptProfile().systemPrompt())
                .isEqualTo("第二版提示");
    }

    @Test
    void agentWithoutReferenceFallsBackToNullProfile() {
        long agentId = insertAgent(9104, null);

        assertThat(conversationService.prepare(agentId, 200L, null).promptProfile()).isNull();
    }

    @Test
    void agentWithDanglingReferenceFallsBackToNullProfile() {
        long agentId = insertAgent(9105, 999_999L);

        assertThat(conversationService.prepare(agentId, 200L, null).promptProfile()).isNull();
    }

    @Test
    void agentReferencingSoftDeletedPresetFallsBackToNullProfile() {
        AgentAiConfigPresetEntity preset = insertPreset("将被删的预设", "提示", null, null);
        long agentId = insertAgent(9106, preset.getId());
        configPresetMapper.deleteById(preset.getId());

        assertThat(conversationService.prepare(agentId, 200L, null).promptProfile()).isNull();
    }

    private AgentAiConfigPresetEntity insertPreset(
            String name, String systemPrompt, String temperature, String defaultSkillIds) {
        AgentAiConfigPresetEntity preset = new AgentAiConfigPresetEntity();
        preset.setName(name);
        preset.setSystemPrompt(systemPrompt);
        preset.setTemperature(temperature == null ? null : new BigDecimal(temperature));
        preset.setDefaultSkillIds(defaultSkillIds);
        configPresetMapper.insert(preset);
        return preset;
    }

    /** {@code tag} 只用于生成互不冲突的 agent_code；主键走库自增，返回实际 id。 */
    private long insertAgent(int tag, Long configPresetId) {
        AgentEntity agent = new AgentEntity();
        agent.setAgentCode("preset-test-" + tag);
        agent.setAgentName("预设测试 " + tag);
        agent.setConfigPresetId(configPresetId);
        agentMapper.insert(agent);
        return agent.getId();
    }
}
