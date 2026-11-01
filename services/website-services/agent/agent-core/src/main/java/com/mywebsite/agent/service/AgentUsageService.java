package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentUsageRecord;

/**
 * AI 用量记账的唯一写入入口（Issue #140）：agent-app 的 Portal 聊天与 /v1 网关、
 * admin 的试聊共用这一份实现，不在各模块另写。记账失败只记日志、绝不向上抛，
 * 调用方不需要也不应该为记账做失败处理。
 *
 * <p>entry_id 归属语义按 {@code source} 区分：ADMIN_PREVIEW（单入口）与
 * GATEWAY_V1（最终应答入口）为精确归属；PORTAL 的备援在 Python 执行层内部
 * 切换，Java 只能按 runMeta 的实际模型名与本轮链比对做 best-effort 判定。
 */
public interface AgentUsageService {

    String SOURCE_PORTAL = "PORTAL";
    String SOURCE_ADMIN_PREVIEW = "ADMIN_PREVIEW";
    String SOURCE_GATEWAY_V1 = "GATEWAY_V1";

    String STATUS_SUCCESS = "SUCCESS";
    String STATUS_UPSTREAM_ERROR = "UPSTREAM_ERROR";

    /** 写入一行用量；内部吞掉全部异常（只 log），不影响任何主流程。 */
    void recordUsage(AgentUsageRecord usage);
}
