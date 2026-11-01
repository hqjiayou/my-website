package com.mywebsite.agent.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.dto.GatewayV1ModelDtos;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 对外 API 网关 {@code /v1/**} 转发：鉴权已在 {@code GatewayAccessKeyAuthFilter} 做完，这里只做 alias 解析与上游转发。 */
public interface GatewayV1Service {

    GatewayV1ModelDtos.ListResponse listModels();

    /**
     * 非流式：{@code requestBody} 的 {@code model} 字段会被原地改写成上游模型名。
     * {@code gatewayAccessKeyId} 来自鉴权过滤器的 request attribute，只用于用量记账归属（Issue #140），可为 null。
     */
    UpstreamResult chatCompletion(ObjectNode requestBody, Long gatewayAccessKeyId);

    /**
     * 流式：直接把上游 SSE 字节逐块写进 {@code response}（已设置 {@code text/event-stream}）。
     * 一旦写出任何字节即视为已提交给客户端，内部不再切换备援。
     */
    void chatCompletionStream(ObjectNode requestBody, HttpServletResponse response, Long gatewayAccessKeyId)
            throws IOException;

    /** 非流式转发结果：{@code status}/{@code contentType}/{@code body} 原样来自上游（或本地合成的失败体）。 */
    record UpstreamResult(int status, String contentType, String body) {}
}
