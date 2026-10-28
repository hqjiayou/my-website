package com.mywebsite.agent.dto;

/**
 * 本轮文档上下文：站点层已从用户上传文件抽取的文本。JSON 字段与 Python {@code ChatDocument} 一致：
 * {@code name}、{@code text}、{@code truncated}。仅作用于本轮，不进历史回放、不落库原文。
 */
public record AgentDocumentContext(String name, String text, boolean truncated) {}
