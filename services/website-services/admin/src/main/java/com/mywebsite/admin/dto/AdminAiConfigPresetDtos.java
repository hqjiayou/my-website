package com.mywebsite.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiConfigPresetDtos {

    private AdminAiConfigPresetDtos() {}

    /** 配置预设（Issue #211 UoW-8）。{@code defaultSkillIds} 为有序 id 集，空列表＝无默认集。 */
    public record AdminAiConfigPresetDto(
            Long id,
            String name,
            String systemPrompt,
            BigDecimal temperature,
            List<String> defaultSkillIds,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiConfigPresetCreateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            List<@Size(max = 128) String> defaultSkillIds) {}

    public record AdminAiConfigPresetUpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            List<@Size(max = 128) String> defaultSkillIds,
            @NotNull Integer version) {}
}
