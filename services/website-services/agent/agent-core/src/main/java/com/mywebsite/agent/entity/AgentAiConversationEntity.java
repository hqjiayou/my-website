package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

@TableName("site_ai_conversation")
public class AgentAiConversationEntity extends AbstractAuditableEntity {

    private Long agentId;
    private Long agentUserId;
    private String title;
    private OffsetDateTime lastMessageAt;

    /** agent Todo 快照 JSON 数组（Flyway V20，TEXT）；NULL = 无，清空时须写回 NULL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentTodos;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(Long agentUserId) {
        this.agentUserId = agentUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(OffsetDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getAgentTodos() {
        return agentTodos;
    }

    public void setAgentTodos(String agentTodos) {
        this.agentTodos = agentTodos;
    }
}
