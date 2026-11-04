package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentMcpSelection;
import java.util.List;

/**
 * MCP per-agent 选择的读写唯一入口（Issue #192 UoW-3）：admin（工作台选择节的
 * GET/PUT 端点）与 agent-app（正式对话组装）及 admin 试聊组装共用这一份实现。
 *
 * <p>三态语义：INHERIT＝用全局启用集（无行等价此态）；OFF＝本 agent 不用 MCP；
 * CUSTOM＝只用名字子集（下发时与全局启用集的交集由执行层过滤完成，
 * 全局已删/已禁的名字自动失效，本服务不预先裁剪）。
 */
public interface AgentMcpSelectionService {

    /** 读某 agent 的选择；无行时回 INHERIT 结果，不抛异常。 */
    AgentMcpSelection getSelection(long agentId);

    /**
     * 保存选择（upsert）。{@code mode} 非三态之一、或 CUSTOM 时名字为空，
     * 抛 {@code BusinessException(BAD_REQUEST)}。INHERIT/OFF 时忽略名字入参。
     */
    AgentMcpSelection saveSelection(long agentId, String mode, List<String> serverNames);

    /**
     * 组装下发口径（见 {@link AgentMcpSelection#wireNames()}）：INHERIT → null、
     * OFF → 空列表、CUSTOM → 名字列表。本方法不做失败隔离：调用方（对话组装）
     * 按经验条目先例自行 catch 后按 INHERIT 继续。
     */
    List<String> resolveWireNames(long agentId);
}
