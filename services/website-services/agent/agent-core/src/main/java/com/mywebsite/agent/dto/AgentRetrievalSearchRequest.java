package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 知识检索请求。{@code topK} 为 null 时由 agent-services 使用默认条数。
 * {@code spaces} 为该 agent 可检索的知识空间集（创建∪订阅）及各空间自己的
 * 登记嵌入类：向量分区键已改 base（Issue #209 UoW-2），嵌入登记已迁空间
 * scope（UoW-3），执行层据此按空间分别用类、跨空间按名次合并，空集回 400；
 * {@code agentId} 过渡期保留作身份字段，不再是分区键（执行层忽略，查询路径
 * 定稿在 UoW-4）。顶层 {@code embeddingSpec}/{@code apiKey}/{@code baseUrl}
 * 是首空间首类的回退凭证（与登记制前的单类下发同形），空间自带类集时以
 * 空间类集为准。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalSearchRequest(
        String agentId,
        String query,
        Integer topK,
        String embeddingSpec,
        String apiKey,
        String baseUrl,
        List<AgentRetrievalSearchSpace> spaces) {

    public AgentRetrievalSearchRequest(
            String agentId, String query, Integer topK, String embeddingSpec, String apiKey, String baseUrl) {
        this(agentId, query, topK, embeddingSpec, apiKey, baseUrl, null);
    }

    public AgentRetrievalSearchRequest(String agentId, String query, Integer topK) {
        this(agentId, query, topK, null, null, null, null);
    }
}
