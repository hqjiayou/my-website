package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentInternalDtos.RuntimeConfigDto;

/**
 * 运行配置拉取（Issue #146 UoW-1）：读 {@code agent_runtime_config} 单行并解密，
 * 供 agent-services 启动时经 internal 通道收敛到页面配置。
 */
public interface AgentInternalRuntimeConfigService {

    /** 无行或 config_version=0 时返回 configured=false 的空配置（不抛异常）。 */
    RuntimeConfigDto currentRuntimeConfig();
}
