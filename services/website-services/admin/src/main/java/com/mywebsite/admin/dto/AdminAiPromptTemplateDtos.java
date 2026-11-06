package com.mywebsite.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiPromptTemplateDtos {

    private AdminAiPromptTemplateDtos() {}

    /** Prompt 模板（Issue #214 UoW-4）。模板只管提示词，不带 skill 集。 */
    public record AdminAiPromptTemplateDto(
            Long id,
            String name,
            String systemPrompt,
            BigDecimal temperature,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiPromptTemplateCreateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature) {}

    public record AdminAiPromptTemplateUpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            @NotNull Integer version) {}

    /**
     * agent 的 Prompt 配置（本地副本口径）：{@code systemPrompt}/{@code temperature}
     * 是 agent 行上的本地副本（解析真相源）；{@code promptTemplateId}/{@code promptTemplateName}
     * 只是来源留痕（模板名解析不到——模板已删——时 name 为 null、id 照实）；
     * {@code defaultSkillIds} 为 agent 自有默认 skill 集（有序，空列表＝无自有集）。
     */
    public record AdminAgentPromptConfigDto(
            Long agentId,
            String systemPrompt,
            BigDecimal temperature,
            Long promptTemplateId,
            String promptTemplateName,
            List<String> defaultSkillIds) {}

    /** 本地副本＋自有技能集整表保存：空白 prompt 落 NULL、空技能集落 NULL；不改来源留痕。 */
    public record AdminAgentPromptConfigUpdateRequest(
            @Size(max = 8000) String systemPrompt,
            @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            List<@Size(max = 128) String> defaultSkillIds) {}

    /** 从模板套用：只拷 prompt 部分并刷新留痕，自有技能集不动。 */
    public record AdminAgentPromptLoadTemplateRequest(@NotNull Long templateId) {}

    /** 把本地副本另存为模板：新建模板并把留痕指向它。 */
    public record AdminAgentPromptSaveAsTemplateRequest(@NotBlank @Size(max = 255) String name) {}
}
