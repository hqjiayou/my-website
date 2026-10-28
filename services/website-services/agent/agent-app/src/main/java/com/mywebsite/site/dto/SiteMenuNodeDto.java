package com.mywebsite.site.dto;

import java.util.List;

public record SiteMenuNodeDto(
        String menuCode, String menuName, String path, String icon, int sort, List<SiteMenuNodeDto> children) {}
