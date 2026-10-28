package com.mywebsite.agent.dto;

import java.util.List;

public record AgentKnowledgeSearchResponseDto(List<Hit> hits, boolean degraded, boolean rerankApplied) {

    public AgentKnowledgeSearchResponseDto {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public AgentKnowledgeSearchResponseDto(List<Hit> hits, boolean degraded) {
        this(hits, degraded, false);
    }

    public record Hit(String sourceId, int chunkIndex, double score, String content) {}
}
