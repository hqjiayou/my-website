package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetUpdateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetUpdateRequest;
import java.util.List;

/**
 * 对话链预设（Issue #210 UoW-7）：平台区 CRUD 与 agent 引用管理。预设是命名的
 * 有序模型入口集合；agent 引用哪条预设，它的对话链就是哪条（换预设即换链）。
 * 被任一 agent 引用的预设禁止删除（先解除引用或改指其他预设）。
 */
public interface AdminAiChainPresetService {

    List<AdminAiChainPresetDto> listPresets();

    AdminAiChainPresetDto getPreset(long id);

    AdminAiChainPresetDto createPreset(AdminAiChainPresetCreateRequest request);

    AdminAiChainPresetDto updatePreset(long id, AdminAiChainPresetUpdateRequest request);

    /** 软删预设；仍被 agent 引用时抛 CONFLICT 并带引用数，不级联改 agent 的引用。 */
    void deletePreset(long id);

    /** agent 当前引用的对话链预设（未引用时 chainPresetId 为 null）。 */
    AdminAgentChainPresetDto getAgentChainPreset(long agentId);

    /** 保存即换链：改 agent 的预设引用（null 为解除引用，对话链随之为空）。 */
    AdminAgentChainPresetDto updateAgentChainPreset(
            long agentId, AdminAgentChainPresetUpdateRequest request);

    /**
     * 组链读路径：agent 引用预设的有序成员入口 id。agent 不存在、未引用预设或
     * 预设已删除时返回空列表（＝无链，诚实无模型）；成员资格与启用状态无关，
     * 启用过滤在组链侧做。
     */
    List<Long> referencedEntryIds(long agentId);
}
