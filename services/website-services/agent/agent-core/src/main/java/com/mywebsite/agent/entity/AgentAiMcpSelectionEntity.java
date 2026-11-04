package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * MCP per-agent 选择（表 {@code agent_ai_mcp_selection}，Flyway V53，Issue #192）。
 * agent 的 1:1 AI 配置旁表，故主键即 {@code agent_id}，不另设代理 id；
 * 无行等价 INHERIT。审计列与平台其余表同形（不继承 AbstractAuditableEntity，
 * 其 {@code @TableId} 固定映射 {@code id} 列）。
 *
 * <p>{@code mode} 三态：INHERIT（用全局启用集）／OFF（明确不用 MCP）／
 * CUSTOM（只用 {@code serverNames} 名字子集，与全局启用集取交集生效）。
 * {@code serverNames} 存 JSON 数组文本，仅 CUSTOM 时有意义。
 */
@TableName("agent_ai_mcp_selection")
public class AgentAiMcpSelectionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String MODE_INHERIT = "INHERIT";
    public static final String MODE_OFF = "OFF";
    public static final String MODE_CUSTOM = "CUSTOM";

    @TableId(value = "agent_id", type = IdType.INPUT)
    private Long agentId;

    private String mode;

    @TableField("server_names")
    private String serverNames;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(value = "updated_by", fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableLogic(value = "false", delval = "true")
    @TableField("deleted")
    private Boolean deleted;

    @TableField("deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getServerNames() {
        return serverNames;
    }

    public void setServerNames(String serverNames) {
        this.serverNames = serverNames;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
