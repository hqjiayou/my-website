package com.mywebsite.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 站点知识检索。{@code query} 上限 16000，与 {@link #MAX_QUERY_CHARS}、站点 chat message 一致。 */
public record AgentKnowledgeSearchRequestDto(
        @NotBlank @Size(max = 16_000) String query, Integer topK) {

    public static final int MAX_QUERY_CHARS = 16_000;

    /** 检索 topK 闭区间上界；超出由服务夹制到 1..该值，null 交给 Python 默认。 */
    public static final int MAX_TOP_K = 20;
}
