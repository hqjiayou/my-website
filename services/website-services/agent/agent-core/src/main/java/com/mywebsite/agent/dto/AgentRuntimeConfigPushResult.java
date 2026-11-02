package com.mywebsite.agent.dto;

/**
 * 运行配置推送结果（Issue #146 UoW-1）。推送失败不抛异常、不回滚已保存的配置：
 * 结果落 {@code agent_runtime_config.last_push_*} 三列，供页面呈现与重试。
 *
 * <ul>
 *   <li>{@code APPLIED}：Python 已换入，{@code appliedVersion} 为其确认的版本；
 *   <li>{@code BLOCKED}：Python 拒绝换入（构建/探活失败或版本 stale），旧配置续跑，
 *       {@code message} 为 Python 回的原因（不含密钥正文）；
 *   <li>{@code FAILED}：网络异常/非 200/token 未配置等通道失败，{@code message} 为简短原因。
 * </ul>
 */
public record AgentRuntimeConfigPushResult(String status, Long appliedVersion, String message) {

    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_FAILED = "FAILED";

    public static AgentRuntimeConfigPushResult applied(Long appliedVersion) {
        return new AgentRuntimeConfigPushResult(STATUS_APPLIED, appliedVersion, null);
    }

    public static AgentRuntimeConfigPushResult blocked(String message) {
        return new AgentRuntimeConfigPushResult(STATUS_BLOCKED, null, message);
    }

    public static AgentRuntimeConfigPushResult failed(String message) {
        return new AgentRuntimeConfigPushResult(STATUS_FAILED, null, message);
    }
}
