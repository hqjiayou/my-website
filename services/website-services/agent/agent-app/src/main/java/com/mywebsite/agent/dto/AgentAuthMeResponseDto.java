package com.mywebsite.agent.dto;

import java.util.List;

/** GET /api/agent/auth/me 载荷（占位：权限 / 菜单后续接入 RBAC）。 */
public record AgentAuthMeResponseDto(
        AgentAuthMeUserDto user,
        List<String> roles,
        List<String> permissions,
        List<AgentMenuNodeDto> menus,
        AgentAuthMeAgentDto agent) {}
