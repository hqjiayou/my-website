package com.mywebsite.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prompt 本地副本解析（Issue #214 UoW-4）：正式对话组装改读 agent 行上的本地
 * 副本（prompt_system_prompt／prompt_temperature／prompt_template_id 留痕）
 * 与自有技能集（default_skill_ids）。以组装快照
 * （{@link ChatPreparation#promptProfile()}／{@code defaultSkills}）对照验收：
 * 存量等价（V70 拷贝形态）、拷贝独立性（改模板/改他人副本不联动）、回退三态、
 * 默认集分化。夹具 tag 用 91xx 段，与其他测试隔离。
 */
class AgentAiPromptResolutionTest extends AbstractAgentSpringBootTest {

    @Autowired
    private AgentAiConversationService conversationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void copiedLocalPromptResolvesSameProfileAsLegacyPreset() {
        // 存量等价快照：旧 profile P（systemPrompt＋temperature、无默认集）经 V60
        // 平移为预设、再经 V70 拷为本地副本后，正式组装出的 promptProfile 与旧
        // 解析逐字一致（留痕指向 V70 生成的模板行）。
        long templateId = insertTemplate("助手一 的配置预设", "你是本站的专属助手。", "0.70");
        long agentId = insertAgent(9101, "你是本站的专属助手。", "0.70", templateId, null);

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.promptProfile())
                .isEqualTo(new AgentPromptProfile("你是本站的专属助手。", 0.7d));
    }

    @Test
    void editingTemplateAfterCopyDoesNotAffectAgentResolution() {
        // 拷贝语义钉子：两个 agent 套用同一模板（V70 回填形态：各持一份内容相同
        // 的本地副本＋同一留痕）；之后改模板，两个 agent 的解析都不变；改其中
        // 一个 agent 的副本，另一个也不受影响。
        long templateId = insertTemplate("共享口径", "第一版提示", null);
        long agentA = insertAgent(9102, "第一版提示", null, templateId, null);
        long agentB = insertAgent(9103, "第一版提示", null, templateId, null);

        jdbcTemplate.update(
                "UPDATE agent_prompt_template SET system_prompt = '第二版提示' WHERE id = ?", templateId);

        assertThat(conversationService.prepare(agentA, 200L, null).promptProfile().systemPrompt())
                .isEqualTo("第一版提示");
        assertThat(conversationService.prepare(agentB, 201L, null).promptProfile().systemPrompt())
                .isEqualTo("第一版提示");

        jdbcTemplate.update(
                "UPDATE agent SET prompt_system_prompt = '甲的专属提示' WHERE id = ?", agentA);

        assertThat(conversationService.prepare(agentA, 200L, null).promptProfile().systemPrompt())
                .isEqualTo("甲的专属提示");
        assertThat(conversationService.prepare(agentB, 201L, null).promptProfile().systemPrompt())
                .isEqualTo("第一版提示");
    }

    @Test
    void ownDefaultSkillsResolveInOrderWithUnknownIdsKept() {
        // 自有集原序透传，未知 id 不在 Java 侧过滤（政策点在 Python）。
        long agentId = insertAgent(9107, "提示", null, null, "[\"skill-b\",\"skill-a\",\"unknown-x\"]");

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.defaultSkills()).containsExactly("skill-b", "skill-a", "unknown-x");
        assertThat(preparation.promptProfile().systemPrompt()).isEqualTo("提示");
    }

    @Test
    void twoAgentsResolveDifferentOwnSkillSets() {
        long agentA = insertAgent(9108, null, null, null, "[\"skill-a\"]");
        long agentB = insertAgent(9109, null, null, null, "[\"skill-b\",\"skill-c\"]");

        assertThat(conversationService.prepare(agentA, 200L, null).defaultSkills())
                .containsExactly("skill-a");
        assertThat(conversationService.prepare(agentB, 201L, null).defaultSkills())
                .containsExactly("skill-b", "skill-c");
    }

    @Test
    void emptyOwnSkillSetLeavesDefaultSkillsNull() {
        long nullSetAgent = insertAgent(9110, "提示", null, null, null);
        long emptySetAgent = insertAgent(9111, "提示", null, null, "[]");
        long bareAgent = insertAgent(9112, null, null, null, null);

        assertThat(conversationService.prepare(nullSetAgent, 200L, null).defaultSkills()).isNull();
        assertThat(conversationService.prepare(emptySetAgent, 200L, null).defaultSkills()).isNull();
        assertThat(conversationService.prepare(bareAgent, 200L, null).defaultSkills()).isNull();
    }

    @Test
    void malformedOwnSkillSetFallsBackToNull() {
        long agentId = insertAgent(9113, "提示", null, null, "{not-json");

        ChatPreparation preparation = conversationService.prepare(agentId, 200L, null);

        assertThat(preparation.defaultSkills()).isNull();
        assertThat(preparation.promptProfile().systemPrompt()).isEqualTo("提示");
    }

    @Test
    void agentWithoutLocalCopyFallsBackToNullProfile() {
        long agentId = insertAgent(9104, null, null, null, null);

        assertThat(conversationService.prepare(agentId, 200L, null).promptProfile()).isNull();
    }

    @Test
    void traceOnlyCopyResolvesNonNullEmptyProfile() {
        // 名称型预设的存量形态：预设只有名称（prompt/温度皆空），旧解析返回
        // 非 null 的空 profile；V70 拷出的「只有留痕」副本须逐字等价。
        long templateId = insertTemplate("只有名称", null, null);
        long agentId = insertAgent(9105, null, null, templateId, null);

        AgentPromptProfile profile = conversationService.prepare(agentId, 200L, null).promptProfile();

        assertThat(profile).isEqualTo(new AgentPromptProfile(null, null));
    }

    @Test
    void temperatureOnlyCopyResolvesProfile() {
        long agentId = insertAgent(9106, null, "0.30", null, null);

        assertThat(conversationService.prepare(agentId, 200L, null).promptProfile())
                .isEqualTo(new AgentPromptProfile(null, 0.3d));
    }

    /** 模板行以 jdbc 直插（agent-core 无模板实体，运行时也不读模板表——解析只读 agent 行）。 */
    private long insertTemplate(String name, String systemPrompt, String temperature) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    java.sql.PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO agent_prompt_template (name, system_prompt, temperature)"
                                    + " VALUES (?, ?, ?)",
                            new String[] {"id"});
                    ps.setString(1, name);
                    ps.setString(2, systemPrompt);
                    if (temperature == null) {
                        ps.setObject(3, null);
                    } else {
                        ps.setBigDecimal(3, new java.math.BigDecimal(temperature));
                    }
                    return ps;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * agent 行以显式高位 id（91xx）直插：共享 H2 里有其他夹具预置的低位 id agent 行，
     * 走自增主键会撞其主键（实测 DuplicateKey）。
     */
    private long insertAgent(
            int tag,
            String promptSystemPrompt,
            String promptTemperature,
            Long promptTemplateId,
            String defaultSkillIds) {
        jdbcTemplate.update(
                "INSERT INTO agent (id, agent_code, agent_name, prompt_system_prompt, prompt_temperature,"
                        + " prompt_template_id, default_skill_ids) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tag,
                "prompt-test-" + tag,
                "Prompt 测试 " + tag,
                promptSystemPrompt,
                promptTemperature == null ? null : new java.math.BigDecimal(promptTemperature),
                promptTemplateId,
                defaultSkillIds);
        return tag;
    }
}
