package com.mywebsite.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.entity.AgentAiConfigPresetEntity;
import com.mywebsite.agent.mapper.AgentAiConfigPresetMapper;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 配置预设解析（Issue #211 UoW-8＋UoW-9）：正式对话组装改走 agent → config_preset_id → 预设。
 * 以组装快照（{@link ChatPreparation#promptProfile()}／{@code presetSkills}）对照验收：
 * 存量等价、共享生效、回退三态、默认集分化。夹具 tag 用 91xx 段，与其他测试隔离。
 */
class AgentAiConfigPresetResolutionTest extends AbstractAgentSpringBootTest {

    @Autowired
    private AgentAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentAiConfigPresetMapper configPresetMapper;

    @Test
    void migratedAgentResolvesSameProfileAsLegacyProfileRow() {
        // 存量等价快照：旧 profile P（systemPrompt＋temperature、无默认集）经 V60
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
    void presetDefaultSkillsResolveInOrderWithUnknownIdsKept() {
        // UoW-9：默认集原序透传，未知 id 不在 Java 侧过滤（政策点在 Python）。
        AgentAiConfigPresetEntity preset =
                insertPreset("带默认集", "提示", null, "[\"skill-b\",\"skill-a\",\"unknown-x\"]");
        long agentId = insertAgent(9107, preset.getId());

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.presetSkills()).containsExactly("skill-b", "skill-a", "unknown-x");
        assertThat(preparation.promptProfile().systemPrompt()).isEqualTo("提示");
    }

    @Test
    void twoAgentsWithDifferentPresetsResolveDifferentSkillSets() {
        long agentA = insertAgent(
                9108, insertPreset("预设甲", null, null, "[\"skill-a\"]").getId());
        long agentB = insertAgent(
                9109, insertPreset("预设乙", null, null, "[\"skill-b\",\"skill-c\"]").getId());

        assertThat(conversationService.prepare(agentA, 200L, null).presetSkills())
                .containsExactly("skill-a");
        assertThat(conversationService.prepare(agentB, 201L, null).presetSkills())
                .containsExactly("skill-b", "skill-c");
    }

    @Test
    void presetWithoutDefaultSkillsLeavesPresetSkillsNull() {
        long nullSetAgent = insertAgent(9110, insertPreset("无默认集", "提示", null, null).getId());
        long emptySetAgent = insertAgent(9111, insertPreset("空默认集", "提示", null, "[]").getId());
        long noPresetAgent = insertAgent(9112, null);

        assertThat(conversationService.prepare(nullSetAgent, 200L, null).presetSkills()).isNull();
        assertThat(conversationService.prepare(emptySetAgent, 200L, null).presetSkills()).isNull();
        assertThat(conversationService.prepare(noPresetAgent, 200L, null).presetSkills()).isNull();
    }

    @Test
    void malformedDefaultSkillsFallBackToNull() {
        long agentId = insertAgent(9113, insertPreset("脏默认集", "提示", null, "{not-json").getId());

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.presetSkills()).isNull();
        assertThat(preparation.promptProfile().systemPrompt()).isEqualTo("提示");
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

    /**
     * agent 行以显式高位 id（91xx）直插：共享 H2 里有其他夹具预置的低位 id agent 行，
     * 走自增主键会撞其主键（实测 DuplicateKey）。预设表只有本类在写，自增安全。
     */
    private long insertAgent(int tag, Long configPresetId) {
        jdbcTemplate.update(
                "INSERT INTO agent (id, agent_code, agent_name, config_preset_id) VALUES (?, ?, ?, ?)",
                tag,
                "preset-test-" + tag,
                "预设测试 " + tag,
                configPresetId);
        return tag;
    }
}
