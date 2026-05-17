package com.mywebsite.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员登录请求（V1 仅支持用户名）")
public record AdminLoginRequestDto(
        @NotBlank @Size(max = 128) @Schema(description = "登录用户名") String username,
        @NotBlank @Schema(description = "密码明文（长度上限由服务端统一校验）") String password
) {}
