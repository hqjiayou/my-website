package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

/** Admin Media HTTP 契约 DTO 聚合。 */
public final class AdminMediaDtos {

    private AdminMediaDtos() {}

    public record AdminMediaListItemDto(
            Long id,
            Long agentId,
            String originalFilename,
            String contentType,
            String extension,
            Long fileSize,
            String status,
            String scope,
            boolean referenced,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminMediaDetailDto(
            Long id,
            Long agentId,
            String originalFilename,
            String contentType,
            String extension,
            Long fileSize,
            String sha256,
            String visibility,
            String status,
            String scope,
            long referenceCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminMediaPreviewDto(Long id, String previewUrl, OffsetDateTime expiresAt) {}

    public record AdminMediaListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String contentTypePrefix,
            String view) {

        public AdminMediaListQuery(Integer pageNum, Integer pageSize, String contentTypePrefix) {
            this(pageNum, pageSize, contentTypePrefix, null);
        }
    }

    public record AdminMediaScopeRequest(String scope) {}
}
