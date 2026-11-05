package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.math.BigDecimal;

/**
 * AI 配置预设（表 {@code agent_ai_config_preset}，Issue #211 UoW-8）：命名的
 * Prompt＋默认 skill 集，agent 以 {@code config_preset_id} 引用、多 agent 可共享。
 * 与 agent-core {@code AgentAiConfigPresetEntity} 同表双映射。
 */
@TableName("agent_ai_config_preset")
public class AdminAiConfigPresetEntity extends AbstractAuditableEntity {

    private String name;
    private String systemPrompt;
    private BigDecimal temperature;
    /** 默认 skill id 的 JSON 数组文本（有序）；NULL/空＝无预设默认集。 */
    private String defaultSkillIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public String getDefaultSkillIds() {
        return defaultSkillIds;
    }

    public void setDefaultSkillIds(String defaultSkillIds) {
        this.defaultSkillIds = defaultSkillIds;
    }
}
