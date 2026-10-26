package com.mywebsite.site.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import com.mywebsite.site.mapper.JsonbStringTypeHandler;

@TableName(value = "site_ai_message", autoResultMap = true)
public class SiteAiMessageEntity extends AbstractAuditableEntity {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private Long conversationId;
    private Long siteId;
    private String role;
    private String content;
    /** ASSISTANT 消息的 Agent 运行元数据 JSON（V21）；不对终端用户暴露。 */
    private String agentMeta;

    /** USER 消息图片附件 JSON 数组原文（V25 JSONB）；ASSISTANT 为 null。 */
    @TableField(value = "attachments", typeHandler = JsonbStringTypeHandler.class)
    private String attachments;

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

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }
}
