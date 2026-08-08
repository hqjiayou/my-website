package com.mywebsite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "前台站点登录请求（siteCode + username）")
public record SiteLoginRequestDto(
        @NotBlank(message = "{site.auth.site-code.required}")
                @Size(max = 64, message = "{site.auth.site-code.max-length}")
                @Schema(description = "站点编码")
                String siteCode,
        @NotBlank(message = "{site.auth.username.required}")
                @Size(max = 128, message = "{site.auth.username.max-length}")
                @Schema(description = "登录用户名")
                String username,
        @NotBlank(message = "{site.auth.password.required}")
                @Size(max = 128, message = "{site.auth.password.too-long}")
                @Schema(description = "密码明文（长度上限由服务端统一校验）")
                String password) {}
