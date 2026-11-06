package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.math.BigDecimal;

@TableName("agent")
public class AgentEntity extends AbstractAuditableEntity {

    private String agentCode;
    private String agentName;
    private String agentType;
    /** V65：类型维度（#213）：SITE＝站点型 / AI_ONLY＝纯 AI 型；与 agentType（自由文本用途标签）无关。 */
    private String agentKind;
    private String status;
    private String defaultDomain;
    private String description;
    /** V8：选用模板；可空。外观真相源在 site_appearance。 */
    private Long templateId;

    /** V70：Prompt 本地副本（#214 UoW-4）；与 promptTemperature、promptTemplateId 三者皆空＝未配置，解析回退为无 profile。 */
    private String promptSystemPrompt;

    /** V70：本地副本温度（0–2）；NULL 用 Provider 默认。 */
    private BigDecimal promptTemperature;

    /** V70：来源模板留痕；仅记录出处，模板变更不联动本地副本。 */
    private Long promptTemplateId;

    /** V70：自有默认 skill 集（有序 JSON 数组文本）；NULL/空＝无自有集，正式对话回退平台 env 默认。 */
    private String defaultSkillIds;

    /** V69：自有对话链·KEY 模式有序 entry_ids（JSON 数组文本）；NULL＝该模式未配链（#214 UoW-3）。 */
    private String chainKeyEntryIds;

    /** V69：自有对话链·CLI 模式有序 entry_ids（JSON 数组文本）；NULL＝该模式未配链（#214 UoW-3）。 */
    private String chainCliEntryIds;

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

    public String getAgentKind() {
        return agentKind;
    }

    public void setAgentKind(String agentKind) {
        this.agentKind = agentKind;
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

    public String getChainKeyEntryIds() {
        return chainKeyEntryIds;
    }

    public void setChainKeyEntryIds(String chainKeyEntryIds) {
        this.chainKeyEntryIds = chainKeyEntryIds;
    }

    public String getChainCliEntryIds() {
        return chainCliEntryIds;
    }

    public void setChainCliEntryIds(String chainCliEntryIds) {
        this.chainCliEntryIds = chainCliEntryIds;
    }

    public String getPromptSystemPrompt() {
        return promptSystemPrompt;
    }

    public void setPromptSystemPrompt(String promptSystemPrompt) {
        this.promptSystemPrompt = promptSystemPrompt;
    }

    public BigDecimal getPromptTemperature() {
        return promptTemperature;
    }

    public void setPromptTemperature(BigDecimal promptTemperature) {
        this.promptTemperature = promptTemperature;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public String getDefaultSkillIds() {
        return defaultSkillIds;
    }

    public void setDefaultSkillIds(String defaultSkillIds) {
        this.defaultSkillIds = defaultSkillIds;
    }
}
