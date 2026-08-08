package com.mywebsite.auth.dto;

import java.time.Instant;

/** 登录成功后的 Body + Refresh Cookie 写入所需数据（非对外 HTTP Body 契约字段）。 */
public record SiteLoginCookieDto(SiteLoginResponseDto loginResponse, String refreshPlain, Instant refreshExpiresAt) {}
