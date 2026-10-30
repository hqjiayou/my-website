package com.mywebsite.agent.service;

/**
 * {@code /v1/**} 自产错误（alias 查无/不支持的入口形状等），由
 * {@code com.mywebsite.agent.handler.GatewayV1ExceptionHandler} 渲染成 OpenAI 错误形状。
 * 上游透传的错误体不走这个类型——那些原样转发，见 {@code GatewayV1ServiceImpl}。
 */
public class GatewayV1ErrorException extends RuntimeException {

    private final int httpStatus;
    private final String type;
    private final String code;

    public GatewayV1ErrorException(int httpStatus, String type, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.type = type;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }
}
