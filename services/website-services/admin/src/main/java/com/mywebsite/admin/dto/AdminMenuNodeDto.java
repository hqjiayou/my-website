package com.mywebsite.admin.dto;

import java.util.List;

public record AdminMenuNodeDto(
        String menuCode,
        String menuName,
        String path,
        String icon,
        int sort,
        List<AdminMenuNodeDto> children,
        Long id,
        String status) {}
