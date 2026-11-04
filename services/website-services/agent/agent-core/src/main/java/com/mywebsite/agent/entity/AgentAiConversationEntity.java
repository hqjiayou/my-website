package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

@TableName("agent_ai_conversation")
public class AgentAiConversationEntity extends AbstractAuditableEntity {

    public static final String SOURCE_PORTAL = "PORTAL";
    public static final String SOURCE_ADMIN_PREVIEW = "ADMIN_PREVIEW";

    private Long agentId;
    /** PORTAL 非空（应用层保证）；ADMIN_PREVIEW 为 NULL（V55）。 */
    private Long agentUserId;
    /** 会话来源（V55）；新建不设则取库默认 PORTAL，与 admin 的 AdminAiConversationEntity 双映射。 */
    private String source;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
