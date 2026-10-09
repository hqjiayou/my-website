package com.mywebsite.agent.dto;

import java.util.List;

/**
 * MCP 深检结果（GET /internal/v1/mcp/ready）。不含 URL。
 * Python 在任一条不可达时返回 HTTP 503，但 body 仍是这份结构；客户端应读 body 而不是只看状态码。
 */
public record AgentMcpReady(boolean ok, String mcpStatus, List<Server> servers, String errorCode, String message) {

    public AgentMcpReady {
        servers = servers == null ? List.of() : List.copyOf(servers);
    }

    public record Server(String name, boolean ok, Integer toolCount, String error) {}
}
