package com.mywebsite.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** agent-services 业务工具回调专用 DTO（internal API，非公开契约）。 */
public final class SiteInternalDtos {

    private SiteInternalDtos() {}

    public record SiteSummaryDto(Long id, String siteName, String siteType, String description) {}

    public record SiteContentItemDto(Long id, String type, String title, String body) {}

    public record SiteContentListDto(List<SiteContentItemDto> items) {}

    public record InternalReservationCreateRequest(
            @NotBlank @Size(max = 128) String customerName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 128) String serviceType,
            @NotNull OffsetDateTime reservedAt,
            @Size(max = 2000) String note) {}

    public record InternalReservationDto(
            Long id, String customerName, String serviceType, OffsetDateTime reservedAt, String status) {}
}
