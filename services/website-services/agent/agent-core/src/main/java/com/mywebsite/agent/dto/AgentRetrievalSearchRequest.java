package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 知识检索请求。{@code topK} 为 null 时由 agent-services 使用默认条数。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalSearchRequest(
        String agentId,
        String query,
        Integer topK,
        String embeddingSpec,
        String apiKey,
        String baseUrl) {

    public AgentRetrievalSearchRequest(String agentId, String query, Integer topK) {
        this(agentId, query, topK, null, null, null);
    }
}