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
    AGENT_BUDGET_EXCEEDED(1008, "common.error.agent-budget-exceeded"),
    /**
     * 400；点名的模型入口不在该模式的生效链上（停用、删除、模式不符，或解不开 key）。
     * 这一轮失败，不得改走该模式的默认入口。
     */
    MODEL_ENTRY_UNAVAILABLE(1009, "common.error.model-entry-unavailable"),
    /**
     * 503；检索配置缺项（如 RETRIEVAL_DATABASE_URL）。索引失败时 message 参数列出缺的键。
     * 不是外部依赖故障，不得收成 {@link #EXTERNAL_DEPENDENCY_BLOCKED}。
     */
    RETRIEVAL_NOT_CONFIGURED(1010, "common.error.retrieval-not-configured"),
    /**
     * 400；调用方点名的模式（CLI / KEY）在该 agent 上没有可用入口（该模式链为空）。
     * 备援封闭在模式内：不得跨模式补链，也不得把这一失败吞成其他口径（如内容为空）。
     * message 参数 {0} 为模式名。
     */
    MODE_NO_AVAILABLE_ENTRY(1011, "common.error.mode-no-available-entry");

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
