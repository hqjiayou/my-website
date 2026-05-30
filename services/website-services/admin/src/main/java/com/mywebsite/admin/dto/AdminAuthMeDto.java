package com.mywebsite.admin.dto;

import java.util.List;

/** GET /api/admin/auth/me 载荷；menus 在 5E 前为空数组。 */
public record AdminAuthMeDto(
        AdminAuthMeUserDto user, List<String> roles, List<String> permissions, List<AdminMenuNodeDto> menus) {}
