package com.mywebsite.site.dto;

/** 站点聊天图片附件。JSON 字段 {@code mediaType}、{@code data}（base64）。 */
public record SiteAiAttachmentDto(String mediaType, String data) {}
