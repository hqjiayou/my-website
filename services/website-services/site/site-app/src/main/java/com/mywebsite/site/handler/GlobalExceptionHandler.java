package com.mywebsite.site.handler;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 前台应用异常出口与 Admin 对齐（BusinessException / 兜底）。当前无带 {@code @Valid} 的 REST 入参，故不挂载
 * {@code MethodArgumentNotValidException}；首個需要 Bean Validation 的 DTO 落地时再在 {@code pom} 增加
 * {@code spring-boot-starter-validation} 并比照 {@code admin} 补全校验分支。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final LocalizedMessageService localizedMessageService;

    public GlobalExceptionHandler(LocalizedMessageService localizedMessageService) {
        this.localizedMessageService = localizedMessageService;
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        String message = localizedMessageService.getMessage(ex.getMessageKey(), ex.getMessageArgs());
        return ApiResponse.fail(ex.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        String message = localizedMessageService.getMessage(ErrorCode.SYSTEM_ERROR.getMessageKey());
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }
}
