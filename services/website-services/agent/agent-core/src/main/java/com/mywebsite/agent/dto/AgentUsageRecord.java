package com.mywebsite.agent.dto;

/**
 * 一条 AI 用量记账入参（Issue #140）。{@code promptTokens}/{@code completionTokens}
 * 为 null 表示本轮拿不到用量（例如网关流式上游未回 usage），落库记 NULL、不记 0。
 * {@code entryId} 的归属语义按来源区分，见 {@code AgentUsageService} 的说明。
 */
public record AgentUsageRecord(
        Long agentId,
        Long entryId,
        String alias,
        String mode,
        String source,
        Long promptTokens,
        Long completionTokens,
        Long latencyMs,
        String status,
        Long gatewayAccessKeyId) {}
