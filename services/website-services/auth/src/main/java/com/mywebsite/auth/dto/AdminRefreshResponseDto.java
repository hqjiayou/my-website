package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "刷新访问令牌响应（新的 Refresh Token 仅通过 Set-Cookie 下发）")
public record AdminRefreshResponseDto(
        @Schema(description = "新的访问令牌 JWT") String accessToken,
        @Schema(description = "Access Token 有效期（秒）") long accessTokenExpiresIn) {}
