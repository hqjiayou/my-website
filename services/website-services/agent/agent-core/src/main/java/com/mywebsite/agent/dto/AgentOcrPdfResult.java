package com.mywebsite.agent.dto;

/** 扫描版 PDF 转写结果：转写文本（Markdown）与实际命中的入口 spec。 */
public record AgentOcrPdfResult(String text, String modelSpec) {}
