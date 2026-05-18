package com.mywebsite.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 统一 HTTP 语义体约定：语义成功以 HTTP Status 为准；可选 {@code code} 为业务语义码（与 HTTP 并行）。
 *
 * <p>成功载荷通常不包含 {@code code}（Jackson 省略 null）；异常响应应显式填入 {@linkplain com.mywebsite.common.enums.ErrorCode ErrorCode}
 * 等与 GlobalExceptionHandler 一致的数值。
 *
 * @param code 省略时表示仅依赖 HTTP Status + message（兼容既有成功响应形态）
 */
@JsonPropertyOrder({"code", "message", "data"})
public record HttpMessageBody<T>(
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer code, String message, T data) {

    /** 不传业务码时使用：JSON 省略 {@code code} 字段。 */
    public HttpMessageBody(String message, T data) {
        this(null, message, data);
    }
}
