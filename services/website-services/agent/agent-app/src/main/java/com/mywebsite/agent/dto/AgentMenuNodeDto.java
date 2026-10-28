package com.mywebsite.agent.dto;

import java.util.List;

public record AgentMenuNodeDto(
        String menuCode, String menuName, String path, String icon, int sort, List<AgentMenuNodeDto> children) {}
