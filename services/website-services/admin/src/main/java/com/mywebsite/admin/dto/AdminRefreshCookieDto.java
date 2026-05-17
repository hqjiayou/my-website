package com.mywebsite.admin.dto;

import java.time.Instant;

/** 刷新成功后的 Body + Refresh Cookie 写入所需数据（非对外 HTTP Body 契约字段）。 */
public record AdminRefreshCookieDto(AdminRefreshResponseDto refreshResponse, String refreshPlain, Instant refreshExpiresAt)
        {}
