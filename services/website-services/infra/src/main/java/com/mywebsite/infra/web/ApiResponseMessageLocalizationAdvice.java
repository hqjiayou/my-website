package com.mywebsite.infra.web;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.enums.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 将语义成功 {@link ApiResponse} 的 {@code message} 规范为固定字面量 {@link ResponseMessages#SUCCESS}，
 * 避免沿用历史上的 messageKey → i18n 差异化成功文案。
 *
 * <p>仅处理 {@linkplain ApiResponse#getCode()} 等于 {@linkplain ErrorCode#SUCCESS} 的写出体；
 * 失败码、{@code HttpMessageBody} 错误信封等不在此改写。
 */
@RestControllerAdvice
public class ApiResponseMessageLocalizationAdvice implements ResponseBodyAdvice<ApiResponse<?>> {

    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public ApiResponse<?> beforeBodyWrite(
            ApiResponse<?> body,
            @NonNull MethodParameter returnType,
            @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull org.springframework.http.server.ServerHttpRequest request,
            @NonNull org.springframework.http.server.ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        if (body.getCode() != ErrorCode.SUCCESS.getCode()) {
            return body;
        }
        return new ApiResponse<>(body.getCode(), ResponseMessages.SUCCESS, body.getData());
    }
}
