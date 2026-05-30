package com.mywebsite.auth.handler;

import com.mywebsite.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * {@link GlobalExceptionHandler} 专用：业务语义码 → HTTP 状态。
 * 置于 {@code handler} 包，避免与持久层 {@code *Mapper} 命名冲突。
 */
public final class GlobalExceptionHttpStatus {

    private GlobalExceptionHttpStatus() {}

    public static HttpStatus fromBusinessCode(int code) {
        if (code == ErrorCode.BAD_REQUEST.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ErrorCode.UNAUTHORIZED.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.FORBIDDEN.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ErrorCode.CONFLICT.getCode()) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
