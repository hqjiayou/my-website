package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 站点知识库原文（表 {@code agent_ai_knowledge_document}）。站点侧只读，写入在 admin。 */
@TableName("agent_ai_knowledge_document")
public class AgentAiKnowledgeDocumentEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long agentId;
    private String title;
    private String content;
    private String status;
    /** 与 admin {@code AdminAiKnowledgeDocumentEntity} 同表双映射的列：admin 侧写入，本侧只声明、不读写。 */
    private String indexEmbeddingSpec;

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

    public String getIndexEmbeddingSpec() {
        return indexEmbeddingSpec;
    }

    public void setIndexEmbeddingSpec(String indexEmbeddingSpec) {
        this.indexEmbeddingSpec = indexEmbeddingSpec;
    }
}
