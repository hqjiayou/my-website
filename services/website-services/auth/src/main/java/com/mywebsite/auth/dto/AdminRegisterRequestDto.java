package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员注册请求（V1）")
public record AdminRegisterRequestDto(
        @NotBlank(message = "{admin.auth.username.required}")
                @Size(max = 128, message = "{admin.auth.username.max-length}")
                @Schema(description = "登录用户名（全平台唯一，区分大小写）")
                String username,
        @NotBlank(message = "{admin.auth.password.required}")
                @Size(min = 8, max = 128, message = "{admin.auth.register.password.policy}")
                @Schema(description = "密码明文（长度见服务端策略）")
                String password,
        @Email(message = "{admin.auth.email.invalid}") @Schema(description = "邮箱（可选，唯一）") String email,
        @Size(max = 255, message = "{admin.auth.display-name.max-length}")
                @Schema(description = "展示名称（可选）")
                String displayName) {}
