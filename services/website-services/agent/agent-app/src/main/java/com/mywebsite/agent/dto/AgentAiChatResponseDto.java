package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mywebsite.agent.dto.AgentCitation;
import java.util.List;

public record AgentAiChatResponseDto(
        String reply, String conversationId, String providerStatus, List<Citation> citations) {

    public AgentAiChatResponseDto {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public AgentAiChatResponseDto(String reply, String conversationId, String providerStatus) {
        this(reply, conversationId, providerStatus, List.of());
    }

    /** 本轮实际采用的检索引用；未 grounding 时为空列表。旧载荷可以没有 title / location / cite。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Citation(
            String sourceId,
            int chunkIndex,
            double score,
            String title,
            AgentCitation.Location location,
            Integer cite) {

        public Citation(String sourceId, int chunkIndex, double score) {
            this(sourceId, chunkIndex, score, null, null, null);
        }
    }
}
