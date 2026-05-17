package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员登录请求（V1 仅支持用户名）")
public record AdminLoginRequestDto(
        @NotBlank(message = "{admin.auth.username.required}")
                @Size(max = 128, message = "{admin.auth.username.max-length}")
                @Schema(description = "登录用户名")
                String username,
        @NotBlank(message = "{admin.auth.password.required}")
                @Size(max = 128, message = "{admin.auth.password.too-long}")
                @Schema(description = "密码明文（长度上限由服务端统一校验）")
                String password) {}
