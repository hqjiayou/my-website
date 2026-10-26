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
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAiKnowledgeDetailDto(
            Long id,
            Long siteId,
            String title,
            String content,
            String status,
            String indexStatus,
            String indexError,
            OffsetDateTime indexedAt,
            String sourceFormat,
            String originalFilename,
            Long fileSizeBytes,
            boolean hasOriginal,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /** 上传元信息（原档留存与去重）：bytes 为原始文件内容，sha256 为其十六进制指纹。 */
    public record AdminAiKnowledgeUploadMeta(
            String originalFilename,
            String sourceFormat,
            String contentType,
            long fileSizeBytes,
            String sha256,
            byte[] bytes) {}

    /** 原档下载（短时 presigned URL）。 */
    public record AdminAiKnowledgeFileDto(String url, String filename, String sourceFormat) {}

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
