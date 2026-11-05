package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 平台模型入口（表 {@code agent_model_entry}）。站点侧只读当前默认入口。
 *
 * <p>入口已平台化（Issue #210 UoW-6）：{@code agentId} 作用域退役、停写恒空（删列留
 * 后续版本），原归属转存 {@code sourceAgentId} 作来源标注；组链按来源层级组装，
 * 不再按请求方 agent 过滤。网关 /v1 仍读 {@code agentId} 走自己的 alias 链规则，
 * 平台化后该值恒空、其作用域分支自然不命中。
 */
@TableName("agent_model_entry")
public class AgentAiModelEntryEntity extends AbstractAuditableEntity {

    private String displayName;
    private String modelSpec;
    private String apiKey;
    private String baseUrl;
    private Long agentId;

    /** 来源标注：平台化前所属的 agent id；平台原生入口为空（Issue #210 UoW-6）。 */
    private Long sourceAgentId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String embeddingSpec;
    private Boolean enabled;

    @TableField("is_default")
    private Boolean defaultEntry;

    /** 双模式归属：KEY=直连 API key；CLI=经 CLI 代理池。旧数据由 DB 默认 KEY。 */
    private String mode;

    /** 对外模型名（网关 UoW-2 用；本步只加字段，不写使用逻辑）。 */
    private String alias;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelSpec() {
        return modelSpec;
    }

    public void setModelSpec(String modelSpec) {
        this.modelSpec = modelSpec;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(Long sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public String getEmbeddingSpec() {
        return embeddingSpec;
    }

    public void setEmbeddingSpec(String embeddingSpec) {
        this.embeddingSpec = embeddingSpec;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getDefaultEntry() {
        return defaultEntry;
    }

    public void setDefaultEntry(Boolean defaultEntry) {
        this.defaultEntry = defaultEntry;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
