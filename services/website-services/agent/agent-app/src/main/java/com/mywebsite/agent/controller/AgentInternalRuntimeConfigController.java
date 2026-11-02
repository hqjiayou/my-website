package com.mywebsite.agent.controller;

import com.mywebsite.agent.dto.AgentInternalDtos.RuntimeConfigDto;
import com.mywebsite.agent.service.AgentInternalRuntimeConfigService;
import com.mywebsite.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行配置拉取（internal only，Issue #146 UoW-1）。运行配置是平台级、不挂 agentId，
 * 故不并入 {@code AgentInternalController} 的 /agents/{agentId} 路径。
 * 鉴权由 {@link com.mywebsite.agent.security.InternalServiceTokenFilter} 承担（/internal/**）。
 */
@Hidden
@RestController
@RequestMapping("/internal/agent/v1")
public class AgentInternalRuntimeConfigController {

    private final AgentInternalRuntimeConfigService runtimeConfigService;

    public AgentInternalRuntimeConfigController(AgentInternalRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping("/runtime-config")
    public ApiResponse<RuntimeConfigDto> getRuntimeConfig() {
        return ApiResponse.success(runtimeConfigService.currentRuntimeConfig());
    }
}
