package com.mywebsite.site.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SiteAiMessageDto(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt,
        List<SiteAiAttachmentDto> attachments,
        List<Citation> citations) {

    /** 本轮实际采用的检索引用。外层列表无引用时为 null，与 {@code attachments} 相同。 */
    public record Citation(String sourceId, int chunkIndex, double score) {}
}
