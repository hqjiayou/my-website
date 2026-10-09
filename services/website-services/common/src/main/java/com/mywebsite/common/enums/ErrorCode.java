package com.mywebsite.common.enums;

/** API 语义码；失败场景对用户可见文案通常由对应 {@linkplain #messageKey} 在多语言资源中解析。 */
public enum ErrorCode {
    /** 语义码 0；HTTP 成功体 {@code message} 固定为 {@code success}，不使用此 messageKey。 */
    SUCCESS(0, "common.result.success"),
    SYSTEM_ERROR(1000, "common.error.system"),
    BAD_REQUEST(1001, "common.error.bad-request"),
    UNAUTHORIZED(1002, "auth.unauthorized"),
    FORBIDDEN(1003, "auth.access.denied"),
    NOT_FOUND(1004, "common.error.not-found"),
    /** 409；资源冲突（如唯一约束重复）。 */
    CONFLICT(1005, "common.error.conflict"),
    /** 429；请求过于频繁（限流 / 冷却中）。 */
    TOO_MANY_REQUESTS(1006, "common.error.too-many-requests"),
    /**
     * 503；外部依赖未配置或明确不可用（如 AI Provider 无 Key）。
     * 不得用伪造成功响应掩盖该状态。
     */
    EXTERNAL_DEPENDENCY_BLOCKED(1007, "common.error.external-dependency-blocked"),
    /**
     * 422；Agent 单轮运行预算超限（请求数 / 工具调用数 / Token）。
     * 对应 agent-services {@code AGENT_BUDGET_EXCEEDED}；非依赖故障，也非入参错误。
     */
    AGENT_BUDGET_EXCEEDED(1008, "common.error.agent-budget-exceeded");

    private final int code;

    /** 稳定 message key（BCP47 前缀不在此体现），由各模块 basename 拼装资源。 */
    private final String messageKey;

    ErrorCode(int code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    public int getCode() {
        return code;
    }

    /**
     * 稳定的多语言消息 key。
     *
     * <p>兼容历史命名：原先对外返回的硬编码英文字符串已替换为此处结构化的 key。
     */
    public String getMessageKey() {
        return messageKey;
    }
}
