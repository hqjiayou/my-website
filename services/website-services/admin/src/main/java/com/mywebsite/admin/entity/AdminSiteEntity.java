package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * Admin 侧对平台 {@code site} 表的双映射实体（与 site-core {@code SiteEntity} 同表不同模块）。
 *
 * <p>字段对齐 V1 + V8（{@code templateId}）；外观字段不落本表，见 {@code site_appearance}。
 * admin 不依赖 site-core，后台 Site Core CRUD 仅通过本 Entity / {@code AdminSiteMapper} 访问。
 */
@TableName("site")
public class AdminSiteEntity extends AbstractAuditableEntity {

    private String siteCode;
    private String siteName;
    private String siteType;
    private String status;
    private String defaultDomain;
    private String description;
    /** V8：选用模板；可空。 */
    private Long templateId;

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDefaultDomain() {
        return defaultDomain;
    }

    public void setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }
}
