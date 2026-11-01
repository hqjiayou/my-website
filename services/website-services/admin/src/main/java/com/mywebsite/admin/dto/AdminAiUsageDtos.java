package com.mywebsite.admin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiUsageDtos {

    private AdminAiUsageDtos() {}

    /** 用量聚合报告（Issue #140 UoW-2）：窗口内一次给全，页面不用二次请求。 */
    public record AdminAiUsageReportDto(
            List<DailyDto> daily, List<ByAgentDto> byAgent, List<ByModelDto> byModel, List<RecentDto> recent) {}

    /** 按 UTC 日聚合；unknownTokenCalls 是窗口内 tokens 记 NULL 的行数（未知不并入 0 静默吞掉）。 */
    public record DailyDto(
            LocalDate date, long calls, long promptTokens, long completionTokens, long unknownTokenCalls) {}

    public record ByAgentDto(Long agentId, long calls, long promptTokens, long completionTokens) {}

    /** 按入口聚合；alias 只有 GATEWAY_V1 行有值，同一入口取窗口内任一非空 alias。 */
    public record ByModelDto(Long entryId, String alias, long calls, long promptTokens, long completionTokens) {}

    public record RecentDto(
            Long id,
            OffsetDateTime createdAt,
            String source,
            Long agentId,
            Long entryId,
            String alias,
            String mode,
            Long promptTokens,
            Long completionTokens,
            Long latencyMs,
            String status,
            Long gatewayAccessKeyId) {}
}
