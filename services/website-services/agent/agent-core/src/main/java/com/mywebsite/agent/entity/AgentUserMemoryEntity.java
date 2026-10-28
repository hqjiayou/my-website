package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

@TableName("site_user_memory")
public class AgentUserMemoryEntity extends AbstractAuditableEntity {

    private Long agentId;
    private Long userId;
    private String content;
    private Long sourceConversationId;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }
}
