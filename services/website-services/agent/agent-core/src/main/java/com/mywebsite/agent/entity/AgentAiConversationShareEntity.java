package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import com.mywebsite.agent.mapper.JsonbStringTypeHandler;

/** 对话分享（V32）：一个会话至多一条；snapshot 为分享时刻快照 JSON，公开读只凭 token。 */
@TableName(value = "agent_ai_conversation_share", autoResultMap = true)
public class AgentAiConversationShareEntity extends AbstractAuditableEntity {

    private Long conversationId;
    private Long agentId;
    private Long agentUserId;
    private String token;

    @TableField(value = "snapshot", typeHandler = JsonbStringTypeHandler.class)
    private String snapshot;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }
}
