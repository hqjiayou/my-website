package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * agent-services 技能目录全量快照（Issue #214 UoW-6）：平台级只读，供工具组
 * Skills 货架页展示。字段与 Python {@code SkillCatalogResponse} 同形——目录项
 * 除 id/说明/版本外还带 license、compatibility、metadata 与 allowed_tools
 * （技能声明的工具面）；顶层带 B 层自选开关的进程实际状态
 * （{@code AGENT_SKILLS_SELF_SELECT_ENABLED}，由 agent-services 进程回报，
 * 管理面只展示、不可改）。技能本体是 agent-services 的文件定义
 * （{@code skills/{id}/SKILL.md}），本 DTO 只含元数据、不含正文。
 */
public record AgentSkillCatalog(
        List<Entry> skills,
        @JsonProperty("skills_self_select_enabled") boolean skillsSelfSelectEnabled) {

    public AgentSkillCatalog {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** 目录条目；{@code allowedTools} 对应 Python 的 {@code allowed_tools}。 */
    public record Entry(
            String id,
            String description,
            String version,
            String license,
            String compatibility,
            Map<String, String> metadata,
            @JsonProperty("allowed_tools") List<String> allowedTools) {

        public Entry {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        }
    }
}
