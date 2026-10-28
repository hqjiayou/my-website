package com.mywebsite.agent.handler;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final LocalizedMessageService localizedMessageService;

    public GlobalExceptionHandler(LocalizedMessageService localizedMessageService) {
        this.localizedMessageService = localizedMessageService;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<HttpMessageBody<Void>> handleBusinessException(BusinessException ex) {
        String message = localizedMessageService.getMessage(ex.getMessageKey(), ex.getMessageArgs());
        return ResponseEntity.status(GlobalExceptionHttpStatus.fromBusinessCode(ex.getCode()))
                .body(new HttpMessageBody<>(ex.getCode(), message, null));
    }

    /**
     * 业务工具（如创建预约）的字段校验失败必须是 400。
     * 落到 {@code Exception} 会变成 500，模型会把它当成服务故障并可能再提交一次。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpMessageBody<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String resolved;
        if (ex.getBindingResult().getFieldErrors().isEmpty()) {
            resolved = localizedMessageService.getMessage(ErrorCode.BAD_REQUEST.getMessageKey());
        } else {
            var fieldError = ex.getBindingResult().getFieldErrors().getFirst();
            String detail = fieldError.getDefaultMessage();
            resolved = detail == null || detail.isBlank()
                    ? fieldError.getField()
                    : fieldError.getField() + ": " + detail;
        }
        return ResponseEntity.badRequest().body(new HttpMessageBody<>(ErrorCode.BAD_REQUEST.getCode(), resolved, null));
    }

    /** 非法 JSON 或无法解析的时间字段。不把异常原文（可能含请求体）写进响应。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<HttpMessageBody<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = localizedMessageService.getMessage(ErrorCode.BAD_REQUEST.getMessageKey());
        return ResponseEntity.badRequest().body(new HttpMessageBody<>(ErrorCode.BAD_REQUEST.getCode(), message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpMessageBody<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = localizedMessageService.getMessage(ErrorCode.SYSTEM_ERROR.getMessageKey());
        return ResponseEntity.internalServerError()
                .body(new HttpMessageBody<>(ErrorCode.SYSTEM_ERROR.getCode(), message, null));
    }
}
