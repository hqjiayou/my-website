package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.math.BigDecimal;

/** 站点 AI Prompt/参数配置（表 {@code site_ai_prompt_profile}）。 */
@TableName("site_ai_prompt_profile")
public class AdminAiPromptProfileEntity extends AbstractAuditableEntity {

    private Long siteId;
    private String systemPrompt;
    private BigDecimal temperature;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
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
