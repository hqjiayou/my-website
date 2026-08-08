package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 平台级系统预置模板目录（表 {@code site_template}）。
 *
 * <p>本实体不映射 pages/blocks 等 Mock 字段；{@code defaultPrimaryColor} 仅为目录展示提示，
 * 非 Appearance 真相源。无 create/update/delete 内容 API。
 */
@TableName("site_template")
public class AdminTemplateEntity extends AbstractAuditableEntity {

    private String code;
    private String name;
    private String siteType;
    private String status;
    private String description;
    private String previewImageUrl;
    private String defaultPrimaryColor;
    private Integer sortOrder;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPreviewImageUrl() {
        return previewImageUrl;
    }

    public void setPreviewImageUrl(String previewImageUrl) {
        this.previewImageUrl = previewImageUrl;
    }

    public String getDefaultPrimaryColor() {
        return defaultPrimaryColor;
    }

    public void setDefaultPrimaryColor(String defaultPrimaryColor) {
        this.defaultPrimaryColor = defaultPrimaryColor;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
