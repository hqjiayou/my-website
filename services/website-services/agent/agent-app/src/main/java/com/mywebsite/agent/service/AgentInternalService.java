package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;

/** agent-services 业务工具回调（Phase 4）；业务真相源保持在 Java。 */
public interface AgentInternalService {

    AgentSummaryDto getAgentSummary(long agentId);

    AgentContentListDto listPublishedContents(long agentId, String type, int limit);
}
