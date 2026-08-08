package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin Template 目录 HTTP 契约 DTO 聚合。 */
public final class AdminTemplateDtos {

    private AdminTemplateDtos() {}

    public record AdminTemplateListItemDto(
            Long id,
            String code,
            String name,
            String siteType,
            String status,
            String previewImageUrl,
            String defaultPrimaryColor,
            Integer sortOrder,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminTemplateDetailDto(
            Long id,
            String code,
            String name,
            String siteType,
            String status,
            String description,
            String previewImageUrl,
            String defaultPrimaryColor,
            Integer sortOrder,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminTemplateStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    public record AdminTemplateListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String status,
            String siteType) {}
}
