package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 知识检索结果。{@code degraded} 为 true 表示检索降级，结果可能不完整。
 * {@code rerankApplied} 为 true 表示结果已经过重排；Python 仅在为 true 时才序列化该字段，缺失按 false。
 */
public record AgentRetrievalSearchResult(List<AgentRetrievalHit> hits, boolean degraded, Boolean rerankApplied) {

    public AgentRetrievalSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        rerankApplied = Boolean.TRUE.equals(rerankApplied);
    }

    public AgentRetrievalSearchResult(List<AgentRetrievalHit> hits, boolean degraded) {
        this(hits, degraded, Boolean.FALSE);
    }
}
