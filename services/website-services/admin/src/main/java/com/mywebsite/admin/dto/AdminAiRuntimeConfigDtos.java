package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AdminAiRuntimeConfigDtos {

    private AdminAiRuntimeConfigDtos() {}

    /**
     * 运行配置视图（Issue #146 UoW-1）：任何情况下不回密钥明文。
     * DSN 只回脱敏形（{@code scheme://host:port/db}，解析失败时为 null、只以
     * {@code retrievalDatabaseUrlConfigured} 表示已配置）；Gemini key 只回配置与否＋末 4 位 hint。
     */
    public record AdminAiRuntimeConfigDto(
            boolean retrievalDatabaseUrlConfigured,
            String retrievalDatabaseUrlMasked,
            String aiEmbeddingModel,
            Integer aiEmbeddingDimensions,
            boolean geminiApiKeyConfigured,
            String geminiApiKeyHint,
            long configVersion,
            String lastPushStatus,
            String lastPushMessage,
            OffsetDateTime lastPushedAt,
            Integer version,
            OffsetDateTime updatedAt) {}

    /**
     * 保存运行配置。密钥字段（{@code retrievalDatabaseUrl}、{@code geminiApiKey}）
     * 留空＝保留原值（照模型入口 update 先例）；非密钥字段按提交值整体替换
     * （{@code aiEmbeddingModel} 空白＝清空，{@code aiEmbeddingDimensions} 为 null＝清空）。
     */
    public record AdminAiRuntimeConfigUpdateRequest(
            @Size(max = 4096) String retrievalDatabaseUrl,
            @Size(max = 256) String aiEmbeddingModel,
            @Positive @Max(10000) Integer aiEmbeddingDimensions,
            @Size(max = 4096) String geminiApiKey) {}
}
