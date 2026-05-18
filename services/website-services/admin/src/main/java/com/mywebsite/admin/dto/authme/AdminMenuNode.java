package com.mywebsite.admin.dto.authme;

import java.util.List;

public record AdminMenuNode(
        String menuCode, String menuName, String path, String icon, int sort, List<AdminMenuNode> children) {}
