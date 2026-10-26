package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 聊天图片附件。JSON 字段与 Python {@code ChatAttachment} 一致：{@code mediaType}、{@code data}（base64）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentAttachmentDto(String mediaType, String data) {}
