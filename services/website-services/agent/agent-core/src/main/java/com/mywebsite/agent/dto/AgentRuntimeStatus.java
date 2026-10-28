package com.mywebsite.agent.dto;

import java.util.List;

/**
 * agent-services 运行时快照（internal health，§22.9）：Provider / Fallback / MCP / 检索 / 生效预算 / harness 文件。
 * 只含配置元数据（spec 名、server 名、数值上限、平台文件是否存在），不含凭证、URL 或文件正文。
 */
public record AgentRuntimeStatus(
        String provider,
        String providerStatus,
        List<String> fallbackProviders,
        String mcpStatus,
        List<McpServer> mcpServers,
        Limits limits,
        String retrievalStatus,
        String retrievalBlockedReason,
        HarnessFiles harness) {

    public AgentRuntimeStatus {
        fallbackProviders = fallbackProviders == null ? List.of() : List.copyOf(fallbackProviders);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        limits = limits == null ? Limits.UNKNOWN : limits;
        harness = harness == null ? HarnessFiles.UNKNOWN : harness;
    }

    public record McpServer(String name, boolean enabled) {}

    /** 旧版 agent-services 未回报时为全 false。 */
    public record HarnessFiles(boolean background, boolean guardrails, boolean toolGuidance) {
        public static final HarnessFiles UNKNOWN = new HarnessFiles(false, false, false);
    }

    /** null = 该维度不限（或旧版 agent-services 未回报）。 */
    public record Limits(
            Integer maxRequests,
            Integer maxToolCalls,
            Integer maxTotalTokens,
            Double toolTimeoutSeconds,
            Integer historyMaxChars) {
        public static final Limits UNKNOWN = new Limits(null, null, null, null, null);
    }
}
