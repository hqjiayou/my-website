package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 知识检索请求。{@code topK} 为 null 时由 agent-services 使用默认条数。
 * {@code baseIds} 为该 agent 可检索的知识空间 id 集（创建∪订阅，字符串化 id）：
 * 向量分区键已改 base（Issue #209 UoW-2），执行层据此跨空间检索且空集回 400；
 * {@code agentId} 过渡期保留作身份字段，不再是分区键（执行层忽略，查询路径定稿在 UoW-4）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalSearchRequest(
        String agentId,
        String query,
        Integer topK,
        String embeddingSpec,
        String apiKey,
        String baseUrl,
        List<AgentEmbeddingClass> embeddingClasses,
        List<String> baseIds) {

    public AgentRetrievalSearchRequest(
            String agentId, String query, Integer topK, String embeddingSpec, String apiKey, String baseUrl) {
        this(agentId, query, topK, embeddingSpec, apiKey, baseUrl, null, null);
    }

    public AgentRetrievalSearchRequest(String agentId, String query, Integer topK) {
        this(agentId, query, topK, null, null, null, null, null);
    }
}
