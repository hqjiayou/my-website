package com.mywebsite.agent.dto;

import com.mywebsite.agent.dto.AgentCitation;
import java.time.OffsetDateTime;
import java.util.List;

public record AgentAiMessageDto(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt,
        List<AgentAiAttachmentDto> attachments,
        List<Citation> citations,
        Process process,
        /** 本轮请求的主用入口 spec。仅 ASSISTANT；旧消息或用户消息为 null。 */
        String requestedModel) {

    /** 本轮实际采用的检索引用。外层列表无引用时为 null，与 {@code attachments} 相同。 */
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

    /**
     * 本轮过程摘要（自 agent_meta 回放）：工具名与成败、知识搜索的查询词与命中数。
     * 不含思考文本、工具参数与返回正文。无过程记录时为 null。
     */
    public record Process(List<Tool> tools, List<Search> searches) {

        public record Tool(String name, boolean ok) {}

        public record Search(String query, int hitCount) {}
    }
}
