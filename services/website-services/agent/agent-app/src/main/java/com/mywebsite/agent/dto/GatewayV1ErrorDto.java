package com.mywebsite.agent.dto;

/**
 * 对外 API 网关 {@code /v1/**} 的错误形状（OpenAI 兼容）：
 * {@code {"error":{"message":...,"type":...,"code":...}}}。
 */
public record GatewayV1ErrorDto(ErrorBody error) {

    /** 命名避免与 {@code java.lang.Error} 同名；JSON 字段名仍是外层 record 的 {@code error}。 */
    public record ErrorBody(String message, String type, String code) {}

    public static GatewayV1ErrorDto of(String message, String type, String code) {
        return new GatewayV1ErrorDto(new ErrorBody(message, type, code));
    }
}
