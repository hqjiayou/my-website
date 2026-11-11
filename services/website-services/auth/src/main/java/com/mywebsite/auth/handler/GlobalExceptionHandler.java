package com.mywebsite.auth.handler;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpMessageBody<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String resolved = ex.getBindingResult().getFieldErrors().isEmpty()
                ? localizedMessageService.getMessage(ErrorCode.BAD_REQUEST.getMessageKey())
                : ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest().body(new HttpMessageBody<>(ErrorCode.BAD_REQUEST.getCode(), resolved, null));
    }

    /** 非法 JSON 反序列化失败 → 4xx；勿落入 catch-all 伪装成 SYSTEM_ERROR(1000)。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<HttpMessageBody<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = localizedMessageService.getMessage(ErrorCode.BAD_REQUEST.getMessageKey());
        return ResponseEntity.badRequest().body(new HttpMessageBody<>(ErrorCode.BAD_REQUEST.getCode(), message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpMessageBody<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = localizedMessageService.getMessage(ErrorCode.SYSTEM_ERROR.getMessageKey());
        return ResponseEntity.internalServerError().body(new HttpMessageBody<>(ErrorCode.SYSTEM_ERROR.getCode(), message, null));
    }
}
