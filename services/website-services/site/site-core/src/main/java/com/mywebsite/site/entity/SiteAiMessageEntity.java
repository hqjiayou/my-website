package com.mywebsite.site.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

@TableName("site_ai_message")
public class SiteAiMessageEntity extends AbstractAuditableEntity {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private Long conversationId;
    private Long siteId;
    private String role;
    private String content;
    /** ASSISTANT 消息的 Agent 运行元数据 JSON（V21）；不对终端用户暴露。 */
    private String agentMeta;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAgentMeta() {
        return agentMeta;
    }

    public void setAgentMeta(String agentMeta) {
        this.agentMeta = agentMeta;
    }
}
