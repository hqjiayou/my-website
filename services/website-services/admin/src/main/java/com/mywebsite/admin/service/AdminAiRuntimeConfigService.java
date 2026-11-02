package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigDto;
import com.mywebsite.admin.dto.AdminAiRuntimeConfigDtos.AdminAiRuntimeConfigUpdateRequest;

/**
 * agent-services 运行配置（Issue #146 UoW-1）：页面配置是真相源，存在
 * {@code agent_runtime_config} 单行；保存落库后立即向 agent-services 推送，
 * 推送结果只更新 last_push_* 状态列，失败不回滚保存。
 */
public interface AdminAiRuntimeConfigService {

    /** 当前配置视图（DSN 脱敏、key 只回 hint，永不回明文）。 */
    AdminAiRuntimeConfigDto getConfig();

    /** 保存（密钥留空保留、config_version +1）后立即推送，返回带最新推送状态的视图。 */
    AdminAiRuntimeConfigDto updateConfig(AdminAiRuntimeConfigUpdateRequest request);

    /** 只重推当前已存配置（不改数据、不递增版本），返回带最新推送状态的视图。 */
    AdminAiRuntimeConfigDto retryPush();
}
