package com.mywebsite.admin.dto;

import java.util.List;
import java.util.Map;

public final class AdminAiSkillCatalogDtos {

    private AdminAiSkillCatalogDtos() {}

    /**
     * Skills 货架（Issue #214 UoW-6）：平台级只读目录，与具体 agent 无关。
     * {@code skillsSelfSelectEnabled} 是 agent-services 进程的 B 层自选开关
     * 实际状态（环境变量决定），管理面只展示、不可改。技能本体是
     * agent-services 的文件定义，本 DTO 只回元数据。
     */
    public record AdminAiSkillCatalogDto(
            List<AdminAiSkillItemDto> skills, boolean skillsSelfSelectEnabled) {

        public AdminAiSkillCatalogDto {
            skills = skills == null ? List.of() : List.copyOf(skills);
        }
    }

    /** 目录条目：{@code allowedTools} 为技能在 SKILL.md 声明的工具面（可为空）。 */
    public record AdminAiSkillItemDto(
            String id,
            String description,
            String version,
            String license,
            String compatibility,
            Map<String, String> metadata,
            List<String> allowedTools) {

        public AdminAiSkillItemDto {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        }
    }
}
