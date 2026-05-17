package com.mywebsite.infra.web;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.infra.i18n.MessageKeys;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 将仍携带 messageKey 的 {@link ApiResponse}（例如 {@link ApiResponse#success()}）在写出前本地化。
 *
 * <p>{@link ApiResponse#fail(int, String)} 若已为自然语言则不匹配 {@link MessageKeys} 正则，不会做二次替换。
 */
@RestControllerAdvice
public class ApiResponseMessageLocalizationAdvice implements ResponseBodyAdvice<ApiResponse<?>> {

    private final LocalizedMessageService localizedMessageService;

    public ApiResponseMessageLocalizationAdvice(LocalizedMessageService localizedMessageService) {
        this.localizedMessageService = localizedMessageService;
    }

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
        String raw = body.getMessage();
        if (!MessageKeys.looksLikeMessageKey(raw)) {
            return body;
        }
        String localized = localizedMessageService.getMessage(raw);
        return new ApiResponse<>(body.getCode(), localized, body.getData());
    }
}
