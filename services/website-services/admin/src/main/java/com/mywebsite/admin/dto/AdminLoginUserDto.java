package com.mywebsite.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应中的管理员基础信息（不含口令）")
public record AdminLoginUserDto(
        @Schema(description = "管理员 ID") long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "账号状态") String status
) {}
