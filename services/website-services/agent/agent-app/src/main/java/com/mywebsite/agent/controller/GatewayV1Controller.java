package com.mywebsite.agent.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.dto.GatewayV1ModelDtos;
import com.mywebsite.agent.security.GatewayAccessKeyAuthFilter;
import com.mywebsite.agent.service.GatewayV1Service;
import com.mywebsite.agent.service.GatewayV1Service.UpstreamResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外 API 网关（OpenAI 兼容形状，UoW-2）。鉴权由独立安全链里的
 * {@code GatewayAccessKeyAuthFilter} 完成，本 Controller 不再校验 Authorization。
 *
 * <p>流式/非流式共用同一路径，用 {@code HttpServletResponse} 直接写，而不是站点 chat/stream
 * 的 {@code SseEmitter}：/v1 要把上游 SSE 原始字节逐块透传，不像站点那样把事件重写成白名单 JSON；
 * 同一方法内按请求体 {@code stream} 决定写 JSON 还是 SSE，两种返回形状无法共用一个强类型返回值。
 */
@RestController
@RequestMapping("/v1")
public class GatewayV1Controller {

    private final GatewayV1Service gatewayV1Service;

    public GatewayV1Controller(GatewayV1Service gatewayV1Service) {
        this.gatewayV1Service = gatewayV1Service;
    }

    @GetMapping("/models")
    public GatewayV1ModelDtos.ListResponse models() {
        return gatewayV1Service.listModels();
    }

    @PostMapping("/chat/completions")
    public void chatCompletions(
            @RequestBody ObjectNode body, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Long accessKeyId = request.getAttribute(GatewayAccessKeyAuthFilter.ACCESS_KEY_ID_ATTRIBUTE) instanceof Long id
                ? id
                : null;
        if (body.path("stream").asBoolean(false)) {
            gatewayV1Service.chatCompletionStream(body, response, accessKeyId);
            return;
        }
        UpstreamResult result = gatewayV1Service.chatCompletion(body, accessKeyId);
        response.setStatus(result.status());
        response.setContentType(result.contentType() != null ? result.contentType() : MediaType.APPLICATION_JSON_VALUE);
        if (result.body() != null) {
            response.getWriter().write(result.body());
        }
    }
}
