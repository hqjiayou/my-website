package com.mywebsite.agent.dto;

/**
 * 运行配置四项的明文载荷（Issue #146）：Java→Python 推送
 * （{@code PUT /internal/v1/config/retrieval}）与 Python→Java 拉取
 * （{@code GET /internal/agent/v1/runtime-config}）共用同一形状。
 *
 * <p>字段为 null 表示该项未配置（推送时由 Python 按「未配置」语义收敛，
 * 拉取时 {@code configVersion=0} 另以 configured=false 区分从未保存）。
 * 本载荷含 DSN 与 key 明文：只许在 internal token 通道内传输，
 * 禁止写入日志、错误信息与任何对外响应。
 */
public record AgentRuntimeConfigPayload(
        String retrievalDatabaseUrl,
        String aiEmbeddingModel,
        Integer aiEmbeddingDimensions,
        String geminiApiKey,
        long configVersion) {}
