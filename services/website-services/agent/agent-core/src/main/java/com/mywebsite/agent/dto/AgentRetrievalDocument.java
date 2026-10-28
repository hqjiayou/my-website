package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** KB 文档派生索引同步载荷；sourceId 建议为 {@code kb:<documentId>}，同键覆盖。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalDocument(
        String agentId,
        String sourceId,
        String title,
        String content,
        String embeddingSpec,
        String apiKey,
        String baseUrl) {

    public AgentRetrievalDocument(String agentId, String sourceId, String title, String content) {
        this(agentId, sourceId, title, content, null, null, null);
    }
}
