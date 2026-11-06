package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.AdminAiSkillCatalogDtos.AdminAiSkillCatalogDto;
import com.mywebsite.admin.dto.AdminAiSkillCatalogDtos.AdminAiSkillItemDto;
import com.mywebsite.admin.service.AdminAiSkillCatalogService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentSkillCatalog;
import org.springframework.stereotype.Service;

/**
 * Skills 货架数据源（Issue #214 UoW-6）：技能目录是 agent-services 的平台
 * 能力，与具体 agent 无关——不再借某个 agent 的上下文取目录（#213 既有
 * 缺口）；预览侧的 per-agent 目录端点保持不动。本服务只做 DTO 映射，
 * 不可达/未配置令牌时由 {@link AgentClient} 抛 BusinessException 透传，
 * 与预览 {@code listSkills} 同口径。
 */
@Service
public class AdminAiSkillCatalogServiceImpl implements AdminAiSkillCatalogService {

    private final AgentClient agentClient;

    public AdminAiSkillCatalogServiceImpl(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @Override
    public AdminAiSkillCatalogDto getCatalog() {
        AgentSkillCatalog catalog = agentClient.skillCatalog();
        return new AdminAiSkillCatalogDto(
                catalog.skills().stream()
                        .map(entry -> new AdminAiSkillItemDto(
                                entry.id(),
                                entry.description(),
                                entry.version(),
                                entry.license(),
                                entry.compatibility(),
                                entry.metadata(),
                                entry.allowedTools()))
                        .toList(),
                catalog.skillsSelfSelectEnabled());
    }
}
