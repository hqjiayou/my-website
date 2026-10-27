package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 扫描版 PDF 转写请求（POST /internal/v1/ocr/pdf）。
 * 模型入口链与 chat 同形状（主用 + 各自带凭证的备援）；contentBase64 为 PDF 原始字节。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentOcrPdfRequest(
        AgentModelSelection model, List<AgentModelSelection> fallbackModels, String contentBase64) {}
