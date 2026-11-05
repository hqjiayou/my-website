package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 检索派生索引同步问题行（Issue #203）：扇出失败落行、定时重试，解决即真删。
 * op=DELETE 为删文档残留（tombstone）；op=UPSERT 为链接分区待同步。
 */
@TableName("agent_ai_knowledge_retrieval_sync_issue")
public class AdminAiKnowledgeRetrievalSyncIssueEntity implements Serializable {

    public static final String OP_DELETE = "DELETE";
    public static final String OP_UPSERT = "UPSERT";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long agentId;
    private String sourceId;
    private String op;
    private Integer attempts;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
}
