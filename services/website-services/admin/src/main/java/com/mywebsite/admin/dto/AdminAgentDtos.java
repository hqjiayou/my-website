package com.mywebsite.admin.dto;

import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin Agent Core HTTP 契约 DTO 聚合。 */
public final class AdminAgentDtos {

    private AdminAgentDtos() {}

    /**
     * 列表项在基础字段之外带关联摘要（#213）：类型维度 {@code agentKind}、订阅知识空间数
     * （订阅表行数，含创建者行，#209 口径），以及链预设／配置预设的 id＋名（预设名解析不到
     * ——已被删或 id 落空——时 name 为 null、id 照实回）。
     */
    public record AdminAgentListItemDto(
            Long id,
            String agentCode,
            String agentName,
            String agentType,
            String status,
            String defaultDomain,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String agentKind,
            long subscriptionCount,
            Long chainPresetId,
            String chainPresetName,
            Long configPresetId,
            String configPresetName) {}

    public record AdminAgentDetailDto(
            Long id,
            String agentCode,
            String agentName,
            String agentType,
            String agentKind,
            String status,
            String defaultDomain,
            String description,
            Long configPresetId,
            Long chainPresetId,
            Integer version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    /** Create 加法：{@code appearance} 可选；给出时同事务写入 agent + site_appearance。 */
    public record AdminAgentCreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$") @Size(max = 64) String agentCode,
            @NotBlank @Size(max = 255) String agentName,
            @NotBlank @Size(max = 32) String agentType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            @Valid AdminAppearanceConfigV1Dto appearance,
            String agentKind) {

        /** 兼容旧调用方：不带 {@code agentKind}（服务层缺省 SITE）。 */
        public AdminAgentCreateRequest(
                String agentCode,
                String agentName,
                String agentType,
                String defaultDomain,
                String description,
                AdminAppearanceConfigV1Dto appearance) {
            this(agentCode, agentName, agentType, defaultDomain, description, appearance, null);
        }
    }

    /** {@code configPresetId} 走全量表单提交口径：给出即引用该预设，null 即清除引用（#211 UoW-8）。 */
    public record AdminAgentUpdateRequest(
            @NotBlank @Size(max = 255) String agentName,
            @Size(max = 32) String agentType,
            @Size(max = 255) String defaultDomain,
            @Size(max = 4000) String description,
            Long configPresetId,
            @NotNull Integer version,
            String agentKind) {

        /** 兼容旧调用方：不带 {@code agentKind}（服务层保留原值，与 agentType 同口径）。 */
        public AdminAgentUpdateRequest(
                String agentName,
                String agentType,
                String defaultDomain,
                String description,
                Long configPresetId,
                Integer version) {
            this(agentName, agentType, defaultDomain, description, configPresetId, version, null);
        }
    }

    public record AdminAgentStatusUpdateRequest(
            @NotBlank @Size(max = 32) String status, @NotNull Integer version) {}

    /** 类型（kind）专属改写（#213）：封闭枚举 SITE/AI_ONLY，乐观锁口径同 status 端点。 */
    public record AdminAgentKindUpdateRequest(
            @NotBlank @Size(max = 16) String kind, @NotNull Integer version) {}

    /** 配置预设引用专属改写（#213）：{@code configPresetId} 为 null 即清除引用，形态仿链预设改引用请求。 */
    public record AdminAgentConfigPresetUpdateRequest(Long configPresetId) {}

    public record AdminAgentListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String status,
            String agentType) {}
}
