package com.mywebsite.common.enums;

public enum ErrorCode {
    SUCCESS(0, "success"),
    SYSTEM_ERROR(1000, "system error"),
    BAD_REQUEST(1001, "bad request"),
    UNAUTHORIZED(1002, "unauthorized"),
    FORBIDDEN(1003, "forbidden"),
    NOT_FOUND(1004, "not found");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
