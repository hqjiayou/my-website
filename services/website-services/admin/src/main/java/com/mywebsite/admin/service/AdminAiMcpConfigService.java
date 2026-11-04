package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigDto;
import com.mywebsite.admin.dto.AdminAiMcpConfigDtos.AdminAiMcpConfigUpdateRequest;

/**
 * MCP 服务器全局注册表（Issue #192 UoW-1）：存 {@code agent_runtime_config}
 * 单行的 {@code mcp_servers_json} 列（整份 JSON 加密），保存后经 #146 同一
 * 推送管线热替换 agent-services 的 MCP 配置；推送失败不回滚保存。
 */
public interface AdminAiMcpConfigService {

    /** 当前注册表视图（headers/env/url 只回 hint，永不回明文）。 */
    AdminAiMcpConfigDto getConfig();

    /** 保存（条目整体替换、密钥留空保留、config_version +1）后立即推送，返回带最新推送状态的视图。 */
    AdminAiMcpConfigDto updateConfig(AdminAiMcpConfigUpdateRequest request);
}
