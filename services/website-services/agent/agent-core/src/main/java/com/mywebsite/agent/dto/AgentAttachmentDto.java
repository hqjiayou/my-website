package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 聊天附件。发往 Python 时只有图片形态，JSON 字段与 Python {@code ChatAttachment} 一致：
 * {@code mediaType}、{@code data}（base64）。
 *
 * <p>落库时同一 JSON 还承载文档描述符：{@code kind=DOCUMENT}、带 {@code fileName} 且 {@code data} 为
 * null（原文与抽取文本都不落库）。历史回放按 data 为空自动跳过文档项，公开 DTO 据 kind 渲染文件芯片。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentAttachmentDto(String mediaType, String data, String fileName, String kind) {

    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_DOCUMENT = "DOCUMENT";

    public AgentAttachmentDto(String mediaType, String data) {
        this(mediaType, data, null, null);
    }

    /** 文档落库描述符：不带原文 data。 */
    public static AgentAttachmentDto documentDescriptor(String mediaType, String fileName) {
        return new AgentAttachmentDto(mediaType, null, fileName, KIND_DOCUMENT);
    }
}
