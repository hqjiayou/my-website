package com.mywebsite.agent.dto;

/**
 * 站点聊天文档附件（原始文件）。JSON 字段 {@code fileName}、{@code mediaType}、{@code data}（base64）。
 * 站点层校验后抽取文本，经 {@code AgentDocumentContext} 发往 agent-services；原文不落库。
 */
public record AgentAiDocumentDto(String fileName, String mediaType, String data) {}
