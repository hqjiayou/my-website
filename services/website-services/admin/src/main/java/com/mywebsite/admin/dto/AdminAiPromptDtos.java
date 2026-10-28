package com.mywebsite.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AdminAiPromptDtos {

    private AdminAiPromptDtos() {}

    /** 站点未配置时字段全空（id 为 null），不视为 404。 */
    public record AdminAiPromptProfileDto(
            Long id,
            Long agentId,
            String systemPrompt,
            BigDecimal temperature,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiPromptProfileUpdateRequest(
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            Integer version) {}
}
