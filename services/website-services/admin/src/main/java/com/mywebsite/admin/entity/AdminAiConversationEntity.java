package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/** 站点 AI 会话（表 {@code agent_ai_conversation}，SoT；PORTAL 行由 agent-app 写入，ADMIN_PREVIEW 行由后台试聊写入）。 */
@TableName("agent_ai_conversation")
public class AdminAiConversationEntity extends AbstractAuditableEntity {

    public static final String SOURCE_PORTAL = "PORTAL";
    public static final String SOURCE_ADMIN_PREVIEW = "ADMIN_PREVIEW";

    private Long agentId;
    /** PORTAL 非空；ADMIN_PREVIEW 为 NULL。 */
    private Long agentUserId;
    /** 会话来源（V55）；新建不设则取库默认 PORTAL。 */
    private String source;
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
}
