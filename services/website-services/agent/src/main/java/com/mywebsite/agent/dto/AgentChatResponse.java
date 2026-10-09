package com.mywebsite.agent.dto;

import java.util.List;

/**
 * agent-services chat 成功体。{@code todos} 为本轮结束时的完整快照（空列表 = 无 todo），
 * 调用方负责持久化/回传以实现跨轮编排；{@code runMeta}（用量 / 工具轨迹 / skills）仅用于观测。
 */
public record AgentChatResponse(
        String reply,
        String conversationId,
        String providerStatus,
        List<AgentTodoItem> todos,
        AgentRunMeta runMeta) {

    public AgentChatResponse {
        todos = todos == null ? List.of() : List.copyOf(todos);
        runMeta = runMeta == null ? AgentRunMeta.EMPTY : runMeta;
    }

    public AgentChatResponse(String reply, String conversationId, String providerStatus) {
        this(reply, conversationId, providerStatus, List.of(), AgentRunMeta.EMPTY);
    }

    public List<String> skillsApplied() {
        return runMeta.skillsApplied();
    }
}
