package com.mywebsite.site.dto;

import java.util.List;

public record SiteKnowledgeSearchResponseDto(List<Hit> hits, boolean degraded) {

    public SiteKnowledgeSearchResponseDto {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public record Hit(String sourceId, int chunkIndex, double score, String content) {}
}
