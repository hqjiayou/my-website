package com.mywebsite.agent.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentKnowledgeDocumentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;
import com.mywebsite.agent.service.AgentInternalService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
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

    /**
     * 知识文档元数据枚举（Issue #181）：集合级问题先枚举全集用。
     * 分页口径定死在 {@code AgentInternalServiceImpl}（limit 默认 100、上限 200）。
     */
    @GetMapping("/knowledge-documents")
    public ApiResponse<AgentKnowledgeDocumentListDto> listKnowledgeDocuments(
            @PathVariable("agentId") long agentId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "createdFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime createdTo,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset) {
        return ApiResponse.success(agentInternalService.listKnowledgeDocuments(
                agentId, keyword, format, createdFrom, createdTo, limit, offset));
    }
}
