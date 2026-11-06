package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AdminAiModelEntryDtos {

    private AdminAiModelEntryDtos() {}

    /**
     * 不含 apiKey。短密钥只显示占位，避免末尾提示把整把 key 露出去。
     * {@code sourceAgentId} 是来源标注（平台化前所属 agent，Issue #210 UoW-6），
     * 平台原生入口为 null；原 {@code agentId} 作用域字段已随入口平台化退役。
     */
    public record AdminAiModelEntryDto(
            Long id,
            String displayName,
            String modelSpec,
            boolean apiKeyConfigured,
            String apiKeyHint,
            String baseUrl,
            Long sourceAgentId,
            String embeddingSpec,
            boolean enabled,
            boolean defaultEntry,
            Integer version,
            OffsetDateTime updatedAt,
            String mode,
            String alias,
            boolean derived,
            String derivedSource) {}

    /** 写入请求不再带 agent 作用域：入口一律平台注册（Issue #210 UoW-6）。 */
    public record AdminAiModelEntryWriteRequest(
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(max = 256) String modelSpec,
            @Size(max = 4096) String apiKey,
            @Size(max = 512) String baseUrl,
            @Size(max = 256) String embeddingSpec,
            @NotNull Boolean enabled,
            @NotNull Boolean defaultEntry,
            Integer version,
            @Size(max = 16) String mode,
            @Size(max = 64) String alias) {}

    /**
     * CLI 派生条目同步报告（Issue #214 UoW-2）。{@code status} 为 OK /
     * PROXY_UNAVAILABLE（代理不可达或管理面未配置，未做任何变更）/
     * NO_CLIENT_KEY（代理就绪但无客户端 key，新模型只能跳过）。
     * {@code deactivated} 是代理侧已消失模型被停用的派生行数（不硬删）。
     */
    public record AdminAiCliEntrySyncReportDto(
            String status,
            boolean proxyReady,
            int created,
            int reactivated,
            int deactivated,
            int unchanged,
            int skippedNoClientKey) {}
}
