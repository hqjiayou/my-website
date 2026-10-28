package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import com.mywebsite.agent.mapper.JsonbStringTypeHandler;

@TableName(value = "agent_ai_message", autoResultMap = true)
public class AgentAiMessageEntity extends AbstractAuditableEntity {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private Long conversationId;
    private Long agentId;
    private String role;
    private String content;
    /** ASSISTANT 消息的 agent_meta JSON（V21）。终端列表只解析其中的 citations，不回传整段。 */
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

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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
