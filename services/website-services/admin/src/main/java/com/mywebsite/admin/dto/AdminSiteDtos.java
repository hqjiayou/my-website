package com.mywebsite.admin.dto;

import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin Site Core HTTP 契约 DTO 聚合。 */
public final class AdminSiteDtos {

    private AdminSiteDtos() {}

    public record AdminSiteListItemDto(
            Long id,
            String siteCode,
            String siteName,
            String siteType,
            String status,
            String defaultDomain,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminSiteDetailDto(
            Long id,
            String siteCode,
            String siteName,
            String siteType,
            String status,
            String defaultDomain,
            String description,
            Long templateId,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /**
     * Create 加法：{@code templateId} 与 {@code appearance} 同有或同无；二者齐全时同事务写入
     * site + site_appearance。
     */
    public record AdminSiteCreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$") @Size(max = 64) String siteCode,
            @NotBlank @Size(max = 255) String siteName,
            @NotBlank @Size(max = 32) String siteType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            Long templateId,
            @Valid AdminAppearanceConfigV1Dto appearance) {}

    public record AdminSiteUpdateRequest(
            @NotBlank @Size(max = 255) String siteName,
            @Size(max = 32) String siteType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            @NotNull Integer version) {}

    public record AdminSiteStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    public record AdminSiteListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String status,
            String siteType) {}
}
