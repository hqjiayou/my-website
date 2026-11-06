package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiCliEntrySyncReportDto;

/**
 * CLI 派生条目同步（Issue #214 UoW-2）：把 CLI 代理当前账号放出的模型
 * 物化为 {@code agent_model_entry} 的派生行（mode=CLI、derived_source=PROXY），
 * 使组链引用目标与人工登记条目同表同口径。同步只维护派生行，人工行不动。
 */
public interface AdminAiCliEntrySyncService {

    /**
     * 按代理当前状态同步一次。代理不可达或管理面未配置时不做任何变更，
     * 报告 status=PROXY_UNAVAILABLE；代理侧已消失的模型只停用派生行不硬删。
     */
    AdminAiCliEntrySyncReportDto syncFromProxy();
}
