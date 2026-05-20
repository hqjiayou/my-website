package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理员注册成功响应（不包含敏感字段）")
public record AdminRegisterResponseDto(
        @Schema(description = "新账号主键 ID") Long id,
        @Schema(description = "登录用户名") String username) {}
