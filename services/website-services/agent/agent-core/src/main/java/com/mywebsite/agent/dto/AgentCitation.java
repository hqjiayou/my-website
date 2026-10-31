package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 对话实际采用的检索引用。未 grounding 时响应里可以没有该列表。
 * title / location / cite 缺省时仍可解析旧的三字段载荷。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentCitation(
        String sourceId,
        int chunkIndex,
        double score,
        String title,
        Location location,
        Integer cite) {

    public AgentCitation(String sourceId, int chunkIndex, double score) {
        this(sourceId, chunkIndex, score, null, null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(Integer page, String sheet, Integer rowStart, Integer rowEnd) {}
}
