package com.mywebsite.infra.web;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.infra.i18n.MessageKeys;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 仅对「语义成功」{@link ApiResponse} 做写出前可选本地化：{@code ApiResponse.success()} 在 {@code common} 模块仍携带 {@code messageKey}
 * ，此处补足为自然语言，避免让每个 Controller 手写 {@code LocalizedMessageService}。
 *
 * <p><b>刻意收窄：</b>仅当 {@linkplain ApiResponse#getCode()} 等于 {@linkplain ErrorCode#SUCCESS} 时再检查 message。
 * {@code Fail}（非零语义）、Security EntryPoint/handler JSON、{@code GlobalExceptionHandler} 等路径不负责在此继续解析，
 * 避免隐式改写错误体或误判。
 *
 * <p>判定仍使用 {@linkplain MessageKeys#looksLikeMessageKey(String)} 启发式；若业务必须把「读起来像 key 的英文字符串」原样透出成功消息，
 * 应避免采用 {@code aaa.bbb} 形态的纯小写点分格式（见 《后端多语言支持策略设计 V1》）。
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
        if (body.getCode() != ErrorCode.SUCCESS.getCode()) {
            return body;
        }
        String raw = body.getMessage();
        if (!MessageKeys.looksLikeMessageKey(raw)) {
            return body;
        }
        String localized = localizedMessageService.getMessage(raw);
        return new ApiResponse<>(body.getCode(), localized, body.getData());
    }
}
