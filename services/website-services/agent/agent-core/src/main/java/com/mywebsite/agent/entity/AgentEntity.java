package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

@TableName("agent")
public class AgentEntity extends AbstractAuditableEntity {

    private String agentCode;
    private String agentName;
    private String agentType;
    private String status;
    private String defaultDomain;
    private String description;
    /** V8：选用模板；可空。外观真相源在 site_appearance。 */
    private Long templateId;
    /** V60：引用的 AI 配置预设（#211）；可空，无预设时对话解析回退为无配置。 */
    private Long configPresetId;

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDefaultDomain() {
        return defaultDomain;
    }

    public void setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getConfigPresetId() {
        return configPresetId;
    }

    public void setConfigPresetId(Long configPresetId) {
        this.configPresetId = configPresetId;
    }
}
