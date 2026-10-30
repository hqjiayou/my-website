package com.mywebsite.agent.handler;

import com.mywebsite.agent.controller.GatewayV1Controller;
import com.mywebsite.agent.dto.GatewayV1ErrorDto;
import com.mywebsite.agent.service.GatewayV1ErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@code /v1/**} 专用错误形状（OpenAI 兼容），与站点其余 API 的 {@code GlobalExceptionHandler}
 * 信封不同，故只对 {@link GatewayV1Controller} 生效（{@code assignableTypes}）。
 * {@code @Order(HIGHEST_PRECEDENCE)} 确保先于全局 advice 命中。
 */
@RestControllerAdvice(assignableTypes = GatewayV1Controller.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayV1ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayV1ExceptionHandler.class);

    @ExceptionHandler(GatewayV1ErrorException.class)
    public ResponseEntity<GatewayV1ErrorDto> handleGatewayError(GatewayV1ErrorException ex) {
        return ResponseEntity.status(ex.httpStatus())
                .body(GatewayV1ErrorDto.of(ex.getMessage(), ex.type(), ex.code()));
    }

    /** 请求体不是合法 JSON：OpenAI 形状里没有专门的 code，按 invalid_request_error 处理。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GatewayV1ErrorDto> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(GatewayV1ErrorDto.of("Invalid JSON request body", "invalid_request_error", "invalid_request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayV1ErrorDto> handleUnexpected(Exception ex) {
        log.error("gateway v1 unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(GatewayV1ErrorDto.of("Internal error", "api_error", "internal_error"));
    }
}
