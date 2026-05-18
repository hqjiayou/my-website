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

    /** 成功态为固定字面量 {@linkplain ResponseMessages#SUCCESS}；异常路径多为本地化后的可读文案或 messageKey。 */
    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    /** 成功态 {@code message} 固定为 {@linkplain ResponseMessages#SUCCESS}（不参与 i18n）。 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ResponseMessages.SUCCESS, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ResponseMessages.SUCCESS, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
