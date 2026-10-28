package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 图片转写请求（知识库图片上传）：与 {@link AgentOcrPdfRequest} 同形状，
 * contentBase64 是图片原始字节，mediaType 是其媒体类型（image/png 等）。
 */
public record AgentOcrImageRequest(
        AgentModelSelection model,
        List<AgentModelSelection> fallbackModels,
        String contentBase64,
        String mediaType) {}
