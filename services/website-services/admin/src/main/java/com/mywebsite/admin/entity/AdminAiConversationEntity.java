package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/** 站点 AI 会话（表 {@code site_ai_conversation}，SoT；写入仅 agent-app）。 */
@TableName("site_ai_conversation")
public class AdminAiConversationEntity extends AbstractAuditableEntity {

    private Long agentId;
    private Long agentUserId;
    private String title;
    private OffsetDateTime lastMessageAt;

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
}
