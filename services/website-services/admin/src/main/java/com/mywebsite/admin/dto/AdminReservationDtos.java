package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin Reservation HTTP 契约 DTO 聚合。 */
public final class AdminReservationDtos {

    private AdminReservationDtos() {}

    public record AdminReservationListItemDto(
            Long id,
            Long siteId,
            String customerName,
            String phone,
            String serviceType,
            OffsetDateTime reservedAt,
            String status,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminReservationDetailDto(
            Long id,
            Long siteId,
            String customerName,
            String phone,
            String serviceType,
            OffsetDateTime reservedAt,
            String status,
            String note,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminReservationCreateRequest(
            @NotBlank @Size(max = 128) String customerName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 128) String serviceType,
            @NotNull OffsetDateTime reservedAt,
            @Size(max = 20000) String note) {}

    public record AdminReservationStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    public record AdminReservationListQuery(
            @Min(1) Integer pageNum, @Min(1) @Max(100) Integer pageSize, String status) {}
}
