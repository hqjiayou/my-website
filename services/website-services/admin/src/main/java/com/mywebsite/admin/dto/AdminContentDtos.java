package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin Content HTTP 契约 DTO 聚合。 */
public final class AdminContentDtos {

    private AdminContentDtos() {}

    public record AdminContentListItemDto(
            Long id,
            Long siteId,
            String type,
            String title,
            String body,
            String status,
            Integer sortOrder,
            Long coverMediaId,
            OffsetDateTime publishedAt,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminContentDetailDto(
            Long id,
            Long siteId,
            String type,
            String title,
            String body,
            String status,
            Integer sortOrder,
            Long coverMediaId,
            OffsetDateTime publishedAt,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminContentCreateRequest(
            @NotBlank @Size(max = 32) String type,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String body,
            @NotBlank @Size(max = 32) String status,
            Integer sortOrder,
            Long coverMediaId) {}

    /** 状态仅走 PATCH {@code /status}，避免绕过 {@code admin:content:status}。 */
    public record AdminContentUpdateRequest(
            @NotBlank @Size(max = 32) String type,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String body,
            Integer sortOrder,
            Long coverMediaId,
            @NotNull Integer version) {}

    public record AdminContentStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    public record AdminContentListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String type,
            String status) {}
}
