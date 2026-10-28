package com.mywebsite.site.dto;

import java.util.List;

public record SiteAiChatResponseDto(
        String reply, String conversationId, String providerStatus, List<Citation> citations) {

    public SiteAiChatResponseDto {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public SiteAiChatResponseDto(String reply, String conversationId, String providerStatus) {
        this(reply, conversationId, providerStatus, List.of());
    }

    /** 本轮实际采用的检索引用；未 grounding 时为空列表。 */
    public record Citation(String sourceId, int chunkIndex, double score) {}
}
