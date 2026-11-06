package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * Admin 侧对平台 {@code agent} 表的双映射实体（与 agent-core {@code AgentEntity} 同表不同模块）。
 *
 * <p>字段对齐 V1 + V8（{@code templateId}）；外观字段不落本表，见 {@code site_appearance}。
 * admin 不依赖 agent-core，后台 Agent Core CRUD 仅通过本 Entity / {@code AdminAgentMapper} 访问。
 */
@TableName("agent")
public class AdminAgentEntity extends AbstractAuditableEntity {

    private String agentCode;
    private String agentName;
    private String agentType;
    /** V65：类型维度（#213）：SITE＝站点型 / AI_ONLY＝纯 AI 型；与 agentType（自由文本用途标签）无关。 */
    private String agentKind;
    private String status;
    private String defaultDomain;
    private String description;
    /** V8：选用模板；可空。 */
    private Long templateId;
    /** V60：引用的 AI 配置预设（#211）；可空。null 即清除引用，须 ALWAYS 否则 updateById 跳过空值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long configPresetId;

    /** V63：引用的对话链预设；#214 UoW-3 起停用（链改存自有字段，见下），列保留待 UoW-7 评估删除。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long chainPresetId;

    /** V69：自有对话链·KEY 模式有序 entry_ids（JSON 数组文本）；NULL＝该模式未配链。清空链须写 null，须 ALWAYS。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String chainKeyEntryIds;

    /** V69：自有对话链·CLI 模式有序 entry_ids（JSON 数组文本）；NULL＝该模式未配链。清空链须写 null，须 ALWAYS。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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

    public Long getChainPresetId() {
        return chainPresetId;
    }

    public void setChainPresetId(Long chainPresetId) {
        this.chainPresetId = chainPresetId;
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

    public Long getConfigPresetId() {
        return configPresetId;
    }

    public void setConfigPresetId(Long configPresetId) {
        this.configPresetId = configPresetId;
    }
}
