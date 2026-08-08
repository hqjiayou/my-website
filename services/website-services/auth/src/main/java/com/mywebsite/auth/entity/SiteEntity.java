package com.mywebsite.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * auth 模块只读站点凭证视图（表 {@code site}）。
 *
 * <p>不依赖 site-core；仅用于 Site 登录时按 {@code site_code} 解析站点状态。
 */
@TableName("site")
public class SiteEntity extends AbstractAuditableEntity {

    private String siteCode;
    private String siteName;
    private String siteType;
    private String status;

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
}
