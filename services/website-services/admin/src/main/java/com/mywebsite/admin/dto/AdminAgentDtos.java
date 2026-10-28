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

/** Admin Agent Core HTTP 契约 DTO 聚合。 */
public final class AdminAgentDtos {

    private AdminAgentDtos() {}

    public record AdminAgentListItemDto(
            Long id,
            String agentCode,
            String agentName,
            String agentType,
            String status,
            String defaultDomain,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminAgentDetailDto(
            Long id,
            String agentCode,
            String agentName,
            String agentType,
            String status,
            String defaultDomain,
            String description,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /** Create 加法：{@code appearance} 可选；给出时同事务写入 agent + site_appearance。 */
    public record AdminAgentCreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$") @Size(max = 64) String agentCode,
            @NotBlank @Size(max = 255) String agentName,
            @NotBlank @Size(max = 32) String agentType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            @Valid AdminAppearanceConfigV1Dto appearance) {}

    public record AdminAgentUpdateRequest(
            @NotBlank @Size(max = 255) String agentName,
            @Size(max = 32) String agentType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            @NotNull Integer version) {}

    public record AdminAgentStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    public record AdminAgentListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String status,
            String agentType) {}
}
