package com.mywebsite.agent.dto;

import java.util.List;

/** agent-services 业务工具回调专用 DTO（internal API，非公开契约）。 */
public final class AgentInternalDtos {

    private AgentInternalDtos() {}

    public record AgentSummaryDto(Long id, String agentName, String agentType, String description) {}

    public record AgentContentItemDto(Long id, String type, String title, String body) {}

    public record AgentContentListDto(List<AgentContentItemDto> items) {}
}
