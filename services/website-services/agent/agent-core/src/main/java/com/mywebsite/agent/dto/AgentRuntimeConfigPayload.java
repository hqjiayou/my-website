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
 *
 * <p>{@code mcpServersJson}（Issue #192）是解密后的 MCP 条目整份 JSON 明文，
 * 语义与其余四项不同：null＝MCP 从未经页面保存，Python 保留当前生效配置
 * （env 基线不被一次无关的运行配置保存冲掉）；页面清空全部条目时传
 * {@code "[]"}，Python 据此收敛为无 MCP。条目内含 headers/env 密钥，
 * 同受上述不落日志纪律约束。
 */
public record AgentRuntimeConfigPayload(
        String retrievalDatabaseUrl,
        String aiEmbeddingModel,
        Integer aiEmbeddingDimensions,
        String geminiApiKey,
        long configVersion,
        String mcpServersJson) {

    /** 兼容不带 MCP 字段的构造点（等价于 mcpServersJson=null：MCP 未经页面保存）。 */
    public AgentRuntimeConfigPayload(
            String retrievalDatabaseUrl,
            String aiEmbeddingModel,
            Integer aiEmbeddingDimensions,
            String geminiApiKey,
            long configVersion) {
        this(retrievalDatabaseUrl, aiEmbeddingModel, aiEmbeddingDimensions, geminiApiKey, configVersion, null);
    }
}
