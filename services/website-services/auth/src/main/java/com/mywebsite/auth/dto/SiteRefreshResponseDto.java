package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "前台刷新响应")
public record SiteRefreshResponseDto(
        @Schema(description = "访问令牌 JWT") String accessToken,
        @Schema(description = "Access Token 有效期（秒）") long accessTokenExpiresIn) {}
