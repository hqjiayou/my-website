package com.mywebsite.site.dto;

/**
 * 站点聊天附件（公开 DTO）。图片为 {@code mediaType}、{@code data}（base64）；
 * 文档项 {@code kind=DOCUMENT}、带 {@code fileName} 且无 {@code data}（原文不落库、不回放）。
 * 旧落库行没有 fileName/kind，前端按图片处理。
 */
public record SiteAiAttachmentDto(String mediaType, String data, String fileName, String kind) {

    public SiteAiAttachmentDto(String mediaType, String data) {
        this(mediaType, data, null, null);
    }
}
