package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/** 站点区块内容（表 {@code site_content}）。 */
@TableName("site_content")
public class AdminContentEntity extends AbstractAuditableEntity {

    /** CMS 表未随改名（site_content/site_appearance/media_file 的 site_id 列保持原名），显式钉住列名。 */
    @TableField("site_id")
    private Long agentId;
    private String type;
    private String title;
    /** 可清空：null 必须写入 DB，不能被 NOT_NULL 策略跳过。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String body;
    private String status;
    private Integer sortOrder;
    /** 可清空：解绑封面时需把 cover_media_id 写成 NULL。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long coverMediaId;
    private OffsetDateTime publishedAt;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(Long coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
