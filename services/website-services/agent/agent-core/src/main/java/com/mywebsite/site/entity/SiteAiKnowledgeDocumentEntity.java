package com.mywebsite.site.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 站点知识库原文（表 {@code site_ai_knowledge_document}）。站点侧只读，写入在 admin。 */
@TableName("site_ai_knowledge_document")
public class SiteAiKnowledgeDocumentEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long siteId;
    private String title;
    private String content;
    private String status;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
