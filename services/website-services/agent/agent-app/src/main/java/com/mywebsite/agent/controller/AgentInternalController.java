package com.mywebsite.agent.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;
import com.mywebsite.agent.service.AgentInternalService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent-services 业务工具回调（internal only）。
 * 鉴权由 {@link com.mywebsite.agent.security.InternalServiceTokenFilter} 承担，不走 JWT。
 */
@Hidden
@RestController
@RequestMapping("/internal/agent/v1/agents/{agentId}")
public class AgentInternalController {

    private final AgentInternalService agentInternalService;

    public AgentInternalController(AgentInternalService agentInternalService) {
        this.agentInternalService = agentInternalService;
    }

    @GetMapping
    public ApiResponse<AgentSummaryDto> getAgentSummary(@PathVariable("agentId") long agentId) {
        return ApiResponse.success(agentInternalService.getAgentSummary(agentId));
    }

    @GetMapping("/contents")
    public ApiResponse<AgentContentListDto> listPublishedContents(
            @PathVariable("agentId") long agentId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        return ApiResponse.success(agentInternalService.listPublishedContents(agentId, type, limit));
    }
}
