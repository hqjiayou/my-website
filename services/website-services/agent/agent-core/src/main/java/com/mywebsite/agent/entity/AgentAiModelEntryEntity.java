package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 平台模型入口（表 {@code agent_model_entry}）。站点侧只读当前默认入口。 */
@TableName("agent_model_entry")
public class AgentAiModelEntryEntity extends AbstractAuditableEntity {

    private String modelSpec;
    private String apiKey;
    private String baseUrl;
    private Long agentId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String embeddingSpec;
    private Boolean enabled;

    @TableField("is_default")
    private Boolean defaultEntry;

    /** 双模式归属：KEY=直连 API key；CLI=经 CLI 代理池。旧数据由 DB 默认 KEY。 */
    private String mode;

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
}
