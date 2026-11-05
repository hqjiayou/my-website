package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * KB 文档派生索引同步载荷；sourceId 建议为 {@code kb:<documentId>}，同键覆盖。
 * 分区键为知识空间 {@code baseId}（Issue #209 UoW-2）：一份文档只写其所属空间一份，
 * 不再按消费 agent 扇出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalDocument(
        String baseId,
        String sourceId,
        String title,
        String content,
        String embeddingSpec,
        String apiKey,
        String baseUrl,
        List<AgentEmbeddingClass> embeddingClasses,
        List<AgentRetrievalSegment> segments,
        List<AgentRetrievalTable> tables,
        /** Issue #202 UoW-2：TRUE＝本次 embeddingClasses 是该空间的完整登记类集，Python 写完后可按差集清理已注销类的残留行；null/false＝子集写（如 fill-missing 补写），不清理。 */
        Boolean embeddingClassesComplete) {

    public AgentRetrievalDocument(
            String baseId,
            String sourceId,
            String title,
            String content,
            String embeddingSpec,
            String apiKey,
            String baseUrl,
            List<AgentEmbeddingClass> embeddingClasses,
            List<AgentRetrievalSegment> segments,
            List<AgentRetrievalTable> tables) {
        this(baseId, sourceId, title, content, embeddingSpec, apiKey, baseUrl,
                embeddingClasses, segments, tables, null);
    }

    public AgentRetrievalDocument(
            String baseId,
            String sourceId,
            String title,
            String content,
            String embeddingSpec,
            String apiKey,
            String baseUrl,
            List<AgentEmbeddingClass> embeddingClasses,
            List<AgentRetrievalSegment> segments) {
        this(baseId, sourceId, title, content, embeddingSpec, apiKey, baseUrl, embeddingClasses, segments, null);
    }

    public AgentRetrievalDocument(
            String baseId,
            String sourceId,
            String title,
            String content,
            String embeddingSpec,
            String apiKey,
            String baseUrl,
            List<AgentEmbeddingClass> embeddingClasses) {
        this(baseId, sourceId, title, content, embeddingSpec, apiKey, baseUrl, embeddingClasses, null, null);
    }

    public AgentRetrievalDocument(
            String baseId,
            String sourceId,
            String title,
            String content,
            String embeddingSpec,
            String apiKey,
            String baseUrl) {
        this(baseId, sourceId, title, content, embeddingSpec, apiKey, baseUrl, null);
    }

    public AgentRetrievalDocument(String baseId, String sourceId, String title, String content) {
        this(baseId, sourceId, title, content, null, null, null);
    }
}
