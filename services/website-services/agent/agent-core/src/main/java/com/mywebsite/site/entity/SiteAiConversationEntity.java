package com.mywebsite.site.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

@TableName("site_ai_conversation")
public class SiteAiConversationEntity extends AbstractAuditableEntity {

    private Long siteId;
    private Long siteUserId;
    private String title;
    private OffsetDateTime lastMessageAt;

    /** agent Todo 快照 JSON 数组（Flyway V20，TEXT）；NULL = 无，清空时须写回 NULL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentTodos;

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
