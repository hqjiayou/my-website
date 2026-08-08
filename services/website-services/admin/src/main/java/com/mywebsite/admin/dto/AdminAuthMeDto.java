package com.mywebsite.admin.dto;

import java.util.List;

/** GET /api/admin/auth/me 载荷；menus 为当前用户可见 MENU 树（5E.2）。 */
public record AdminAuthMeDto(
        AdminAuthMeUserDto user, List<String> roles, List<String> permissions, List<AdminMenuNodeDto> menus) {}
