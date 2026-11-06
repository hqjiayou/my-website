package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.math.BigDecimal;

/**
 * Prompt 模板（表 {@code agent_prompt_template}，Issue #214 UoW-4）：命名的
 * 提示词资产（名称＋system prompt＋temperature）。agent 套用即拷贝为本地
 * 副本、之后互不联动；模板不带 skill 集（技能归 agent 自有集，性质不同，
 * 自配置预设拆分起分开维护）。
 */
@TableName("agent_prompt_template")
public class AdminAiPromptTemplateEntity extends AbstractAuditableEntity {

    private String name;
    private String systemPrompt;
    private BigDecimal temperature;

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
}
