package com.mywebsite.site.dto;

/** 会话分享引用。前端以当前 origin 拼 {@code /share/{token}} 得到完整链接。 */
public record SiteAiShareDto(String token) {}
