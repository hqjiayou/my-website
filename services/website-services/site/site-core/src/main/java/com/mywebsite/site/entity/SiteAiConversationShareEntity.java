package com.mywebsite.site.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import com.mywebsite.site.mapper.JsonbStringTypeHandler;

/** 对话分享（V32）：一个会话至多一条；snapshot 为分享时刻快照 JSON，公开读只凭 token。 */
@TableName(value = "site_ai_conversation_share", autoResultMap = true)
public class SiteAiConversationShareEntity extends AbstractAuditableEntity {

    private Long conversationId;
    private Long siteId;
    private Long siteUserId;
    private String token;

    @TableField(value = "snapshot", typeHandler = JsonbStringTypeHandler.class)
    private String snapshot;

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

    public Long getSiteUserId() {
        return siteUserId;
    }

    public void setSiteUserId(Long siteUserId) {
        this.siteUserId = siteUserId;
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
