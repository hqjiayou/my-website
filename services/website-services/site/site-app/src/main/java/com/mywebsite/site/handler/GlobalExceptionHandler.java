package com.mywebsite.site.handler;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String resolved = ex.getBindingResult().getFieldErrors().isEmpty()
                ? localizedMessageService.getMessage(ErrorCode.BAD_REQUEST.getMessageKey())
                : ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), resolved);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        String message = localizedMessageService.getMessage(ErrorCode.SYSTEM_ERROR.getMessageKey());
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }
}
