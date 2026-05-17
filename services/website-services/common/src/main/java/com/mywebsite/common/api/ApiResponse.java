package com.mywebsite.common.api;

import com.mywebsite.common.enums.ErrorCode;

public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    /** 通常为 messageKey（成功态）或由 handler 本地化后的可读文案。 */
    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    /** message 中为 {@linkplain ErrorCode#SUCCESS} 的 messageKey（由 infra 切面再本地化）。 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessageKey(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessageKey(), data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
