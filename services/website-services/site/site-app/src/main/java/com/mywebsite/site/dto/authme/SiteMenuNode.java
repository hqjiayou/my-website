package com.mywebsite.site.dto.authme;

import java.util.List;

public record SiteMenuNode(
        String menuCode, String menuName, String path, String icon, int sort, List<SiteMenuNode> children) {}
