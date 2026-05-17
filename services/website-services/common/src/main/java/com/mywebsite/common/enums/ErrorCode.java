package com.mywebsite.common.enums;

/** API 语义码；对用户可见文案由对应 {@linkplain #messageKey} 在多语言资源中解析。 */
public enum ErrorCode {
    SUCCESS(0, "common.result.success"),
    SYSTEM_ERROR(1000, "common.error.system"),
    BAD_REQUEST(1001, "common.error.bad-request"),
    UNAUTHORIZED(1002, "auth.unauthorized"),
    FORBIDDEN(1003, "auth.access.denied"),
    NOT_FOUND(1004, "common.error.not-found");

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
