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
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminMediaPreviewDto(Long id, String previewUrl, OffsetDateTime expiresAt) {}

    public record AdminMediaListQuery(
            @Min(1) Integer pageNum, @Min(1) @Max(100) Integer pageSize, String contentTypePrefix) {}
}
