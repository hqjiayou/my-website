package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 站点知识库原文（表 {@code agent_ai_knowledge_document}）。站点侧只读，写入在 admin。 */
@TableName("agent_ai_knowledge_document")
public class AgentAiKnowledgeDocumentEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 所属知识空间（V29 列，admin 侧写入）。文档只属一个空间：站点侧一切文档
     * 可见性过滤按本列对 agent 可达空间集（创建∪订阅）判定（Issue #209 UoW-4
     * 定稿）；文档原 {@code agent_id} 列已随 UoW-5 的 Flyway V62 删除。
     */
    private Long baseId;

    private String title;
    private String content;
    private String status;
    /** 与 admin {@code AdminAiKnowledgeDocumentEntity} 同表双映射的列：admin 侧写入，本侧只声明、不读写。 */
    private String indexEmbeddingSpec;

    /** 派生索引状态（V27 列，admin 侧写入）；internal 文档枚举只读回给 agent-services。 */
    private String indexStatus;

    /** 来源格式（V28 列，admin 侧写入，如 pdf/docx/xlsx/md）；枚举的「类型」过滤按此列。 */
    private String sourceFormat;

    /** 原始文件名（V28 列，admin 侧写入）；枚举元数据回给模型辨认文档。 */
    private String originalFilename;

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
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

    public String getIndexEmbeddingSpec() {
        return indexEmbeddingSpec;
    }

    public void setIndexEmbeddingSpec(String indexEmbeddingSpec) {
        this.indexEmbeddingSpec = indexEmbeddingSpec;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
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
}
