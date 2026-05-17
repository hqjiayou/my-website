package com.mywebsite.common.exception;

import com.mywebsite.common.enums.ErrorCode;
import java.util.Arrays;

/**
 * 携带 HTTP 语义码与多语言 {@linkplain #messageKey}；解析由各应用中的 {@code LocalizedMessageService} 等在 handler 层完成，
 * 以保持 {@code common} 不依赖 Spring / infra。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    private final String messageKey;

    private final Object[] messageArgs;

    /** 沿用通用 {@link ErrorCode} 的默认 messageKey。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
        this.messageArgs = new Object[0];
    }

    /** 同上，并附加 MessageFormat 占位符参数（由 MessageSource 解释）。 */
    public BusinessException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.getMessageKey());
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getMessageKey();
        this.messageArgs =
                messageArgs == null ? new Object[0] : Arrays.copyOf(messageArgs, messageArgs.length);
    }

    /**
     * 相同语义码 {@code code}，但使用更细的业务 messageKey（例如 {@code admin.auth.login.failed}）。
     */
    public BusinessException(int code, String messageKey, Object... messageArgs) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs =
                messageArgs == null ? new Object[0] : Arrays.copyOf(messageArgs, messageArgs.length);
    }

    public int getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return Arrays.copyOf(messageArgs, messageArgs.length);
    }
}
