package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiKnowledgeDtos {

    private AdminAiKnowledgeDtos() {}

    public record AdminAiKnowledgeListItemDto(
            Long id,
            Long siteId,
            String title,
            String status,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAiKnowledgeDetailDto(
            Long id,
            Long siteId,
            String title,
            String content,
            String status,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAiKnowledgeCreateRequest(
            @NotBlank @Size(max = 255) String title, @NotBlank String content) {}

    public record AdminAiKnowledgeUpdateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String content,
            @NotBlank String status,
            Integer version) {}

    public record AdminAiKnowledgeListQuery(Integer pageNum, Integer pageSize, String status) {}

    public record AdminAiKnowledgeReembedConflictDto(Long siteId, Integer dimensions) {}

    public record AdminAiKnowledgeReembedFailureDto(Long siteId, String sourceId, String error) {}

    public record AdminAiKnowledgeReembedResultDto(
            int sites,
            int documents,
            Integer dimensions,
            boolean recreatedSchema,
            List<AdminAiKnowledgeReembedFailureDto> failed,
            String status) {}

    public record AdminAiKnowledgeReembedConflictResponse(
            String message, List<AdminAiKnowledgeReembedConflictDto> conflicts) {}
}
