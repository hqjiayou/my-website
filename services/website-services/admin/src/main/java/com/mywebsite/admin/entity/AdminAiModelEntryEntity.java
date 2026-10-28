package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 平台模型入口（表 {@code agent_model_entry}）。{@code apiKey} 不得写入接口响应。 */
@TableName("agent_model_entry")
public class AdminAiModelEntryEntity extends AbstractAuditableEntity {

    private String displayName;
    private String modelSpec;
    private String apiKey;

    /** 可清空：null 必须写入 DB，不能被 NOT_NULL 策略跳过。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String baseUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long agentId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String embeddingSpec;
    private Boolean enabled;

    @TableField("is_default")
    private Boolean defaultEntry;
    private String mode;

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
