package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/**
 * agent-services 运行配置（表 {@code agent_runtime_config}，Flyway V47，Issue #146）。
 * 平台单行（id 固定 1，V47 预置空行）：admin 写（页面配置是真相源），agent-app 读
 * （internal 拉取端点供 Python 启动收敛）。两个密钥字段存 {@code ModelEntryCipher} 密文。
 *
 * <p>{@code configVersion} 是配置版本（每次有效保存 +1），与审计基类的乐观锁列
 * {@code version} 是两回事，列名 {@code config_version} 不可改。
 */
@TableName("agent_runtime_config")
public class AgentRuntimeConfigEntity extends AbstractAuditableEntity {

    /** 平台单行固定主键（DB 层 CHECK (id = 1) 兜底）。 */
    public static final long SINGLETON_ID = 1L;

    private String retrievalDatabaseUrl;
    private String aiEmbeddingModel;
    private Integer aiEmbeddingDimensions;
    private String geminiApiKey;

    @TableField("config_version")
    private Long configVersion;

    @TableField("last_push_status")
    private String lastPushStatus;

    @TableField("last_push_message")
    private String lastPushMessage;

    @TableField("last_pushed_at")
    private OffsetDateTime lastPushedAt;

    public String getRetrievalDatabaseUrl() {
        return retrievalDatabaseUrl;
    }

    public void setRetrievalDatabaseUrl(String retrievalDatabaseUrl) {
        this.retrievalDatabaseUrl = retrievalDatabaseUrl;
    }

    public String getAiEmbeddingModel() {
        return aiEmbeddingModel;
    }

    public void setAiEmbeddingModel(String aiEmbeddingModel) {
        this.aiEmbeddingModel = aiEmbeddingModel;
    }

    public Integer getAiEmbeddingDimensions() {
        return aiEmbeddingDimensions;
    }

    public void setAiEmbeddingDimensions(Integer aiEmbeddingDimensions) {
        this.aiEmbeddingDimensions = aiEmbeddingDimensions;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public String getLastPushStatus() {
        return lastPushStatus;
    }

    public void setLastPushStatus(String lastPushStatus) {
        this.lastPushStatus = lastPushStatus;
    }

    public String getLastPushMessage() {
        return lastPushMessage;
    }

    public void setLastPushMessage(String lastPushMessage) {
        this.lastPushMessage = lastPushMessage;
    }

    public OffsetDateTime getLastPushedAt() {
        return lastPushedAt;
    }

    public void setLastPushedAt(OffsetDateTime lastPushedAt) {
        this.lastPushedAt = lastPushedAt;
    }
}
