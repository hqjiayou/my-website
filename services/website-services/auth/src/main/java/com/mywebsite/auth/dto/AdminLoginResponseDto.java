package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应（Access Token 在 Body；Refresh Token 仅写入 HttpOnly Cookie）")
public record AdminLoginResponseDto(
        @Schema(description = "访问令牌 JWT") String accessToken,
        @Schema(description = "Access Token 有效期（秒）") long accessTokenExpiresIn,
        @Schema(description = "当前管理员基础信息") AdminLoginUserDto adminUser) {}
