package com.mywebsite.agent.dto;

import java.util.List;

/** 知识检索结果。{@code degraded} 为 true 表示检索降级，结果可能不完整。 */
public record AgentRetrievalSearchResult(List<AgentRetrievalHit> hits, boolean degraded) {

    public AgentRetrievalSearchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
