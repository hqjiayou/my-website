package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/** 平台媒体元数据（表 {@code media_file}）；{@code agentId}（site_id）为上传来源留痕，非归属。 */
@TableName("media_file")
public class AdminMediaEntity extends AbstractAuditableEntity {

    /** CMS 表未随改名（site_content/site_appearance/media_file 的 site_id 列保持原名），显式钉住列名。 */
    @TableField("site_id")
    private Long agentId;
    private String bucket;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private String extension;
    private Long fileSize;
    private String sha256;
    private String visibility;
    /** 可见范围：AGENT=仅来源站与引用站可见 / PLATFORM=平台可见（V57）。 */
    private String scope;
    private String status;
    /** 对象存储已清理时间；非空表示不再进入 orphan 候选。 */
    private OffsetDateTime objectPurgedAt;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getObjectPurgedAt() {
        return objectPurgedAt;
    }

    public void setObjectPurgedAt(OffsetDateTime objectPurgedAt) {
        this.objectPurgedAt = objectPurgedAt;
    }
}
