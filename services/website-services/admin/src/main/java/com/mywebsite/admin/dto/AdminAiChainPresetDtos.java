package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminAiChainPresetDtos {

    private AdminAiChainPresetDtos() {}

    /** 对话链模板（Issue #210 UoW-7 引入，#214 UoW-3 降级为模板）。{@code entryIds} 为有序成员入口 id 集，空列表＝无成员。 */
    public record AdminAiChainPresetDto(
            Long id,
            String name,
            String purpose,
            List<Long> entryIds,
            Integer version,
            OffsetDateTime updatedAt) {}

    public record AdminAiChainPresetCreateRequest(
            @NotBlank @Size(max = 255) String name, List<Long> entryIds) {}

    public record AdminAiChainPresetUpdateRequest(
            @NotBlank @Size(max = 255) String name, List<Long> entryIds, @NotNull Integer version) {}

    /**
     * agent 自有对话链（Issue #214 UoW-3）：按模式各一份有序 entry ids；
     * 空列表＝该模式未配链（对话链为空、诚实 BLOCKED）。
     */
    public record AdminAgentChainDto(Long agentId, List<Long> keyEntryIds, List<Long> cliEntryIds) {}

    /** 整表保存某模式自有链：{@code entryIds} 即该模式链的完整有序成员（空＝清空）。 */
    public record AdminAgentChainUpdateRequest(List<Long> entryIds) {}

    /** 从模板载入：把模板成员中属于 {@code mode} 的部分按模板序拷入该模式自有链。 */
    public record AdminAgentChainLoadTemplateRequest(
            @NotBlank String mode, @NotNull Long templateId) {}

    /** 把某模式自有链另存为模板（新建一条链预设，成员即该模式链当前内容）。 */
    public record AdminAgentChainSaveAsTemplateRequest(
            @NotBlank String mode, @NotBlank @Size(max = 255) String name) {}
}
