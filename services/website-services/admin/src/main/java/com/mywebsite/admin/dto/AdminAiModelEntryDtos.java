package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AdminAiModelEntryDtos {

    private AdminAiModelEntryDtos() {}

    /** 不含 apiKey。短密钥只显示占位，避免末尾提示把整把 key 露出去。 */
    public record AdminAiModelEntryDto(
            Long id,
            String displayName,
            String modelSpec,
            boolean apiKeyConfigured,
            String apiKeyHint,
            String baseUrl,
            Long agentId,
            String embeddingSpec,
            boolean enabled,
            boolean defaultEntry,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiModelEntryWriteRequest(
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(max = 256) String modelSpec,
            @Size(max = 4096) String apiKey,
            @Size(max = 512) String baseUrl,
            Long agentId,
            @Size(max = 256) String embeddingSpec,
            @NotNull Boolean enabled,
            @NotNull Boolean defaultEntry,
            Integer version) {}
}
