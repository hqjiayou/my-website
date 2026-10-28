package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** AI 知识库文档（表 {@code site_ai_knowledge_document}，KB SoT）。 */
@TableName("site_ai_knowledge_document")
public class AdminAiKnowledgeDocumentEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 派生索引状态：PENDING=未在索引中（新建待同步/已停用），INDEXED=已索引，FAILED=上次同步失败。 */
    public static final String INDEX_STATUS_PENDING = "PENDING";

    public static final String INDEX_STATUS_INDEXED = "INDEXED";
    public static final String INDEX_STATUS_FAILED = "FAILED";

    private Long agentId;
    private String title;
    private String content;
    private String status;
    private String indexStatus;
    private String indexError;
    private java.time.OffsetDateTime indexedAt;
    private Long baseId;
    private String sourceFormat;
    private String originalFilename;
    private String storageBucket;
    private String storageKey;
    private Long fileSizeBytes;
    private String sha256;

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public String getIndexError() {
        return indexError;
    }

    public void setIndexError(String indexError) {
        this.indexError = indexError;
    }

    public java.time.OffsetDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(java.time.OffsetDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}
