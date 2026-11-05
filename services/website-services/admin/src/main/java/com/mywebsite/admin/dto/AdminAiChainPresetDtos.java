package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiChainPresetDtos {

    private AdminAiChainPresetDtos() {}

    /** 对话链预设（Issue #210 UoW-7）。{@code entryIds} 为有序成员入口 id 集，空列表＝无成员。 */
    public record AdminAiChainPresetDto(
            Long id,
            String name,
            String purpose,
            List<Long> entryIds,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiChainPresetCreateRequest(
            @NotBlank @Size(max = 255) String name, List<Long> entryIds) {}

    public record AdminAiChainPresetUpdateRequest(
            @NotBlank @Size(max = 255) String name, List<Long> entryIds, @NotNull Integer version) {}

    /** agent 当前引用的对话链预设；{@code chainPresetId} 为 null 表示未引用（对话链为空）。 */
    public record AdminAgentChainPresetDto(Long agentId, Long chainPresetId) {}

    /** 改引用请求：{@code chainPresetId} 为 null 表示解除引用。 */
    public record AdminAgentChainPresetUpdateRequest(Long chainPresetId) {}
}
