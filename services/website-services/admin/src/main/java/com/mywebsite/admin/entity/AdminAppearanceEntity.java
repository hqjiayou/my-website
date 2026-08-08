package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.admin.mapper.JsonbStringTypeHandler;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 站点外观（表 {@code site_appearance}）。{@code config} 以 JSON 文本持久化（PG JSONB / H2 文本），
 * 业务闭集校验在 Service；本实体不映射 logo / homeSections 等本版剔出字段。
 */
@TableName(value = "site_appearance", autoResultMap = true)
public class AdminAppearanceEntity extends AbstractAuditableEntity {

    private Long siteId;
    private Integer schemaVersion;

    /** JSON 文本；列类型见 Flyway V8（JSONB）。 */
    @TableField(value = "config", typeHandler = JsonbStringTypeHandler.class)
    private String configJson;

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
