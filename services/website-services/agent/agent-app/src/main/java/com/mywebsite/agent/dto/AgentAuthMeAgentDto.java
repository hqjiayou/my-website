package com.mywebsite.agent.dto;

/** GET /api/agent/auth/me 载荷中的当前站点。 */
public record AgentAuthMeAgentDto(long agentId, String agentCode, String agentName) {}
