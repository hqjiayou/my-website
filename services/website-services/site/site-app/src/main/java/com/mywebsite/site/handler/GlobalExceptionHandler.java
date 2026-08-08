package com.mywebsite.site.handler;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.service.LocalizedMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpMessageBody<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = localizedMessageService.getMessage(ErrorCode.SYSTEM_ERROR.getMessageKey());
        return ResponseEntity.internalServerError()
                .body(new HttpMessageBody<>(ErrorCode.SYSTEM_ERROR.getCode(), message, null));
    }
}
