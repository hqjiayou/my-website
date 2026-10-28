package com.mywebsite.agent.dto;

import java.util.List;

/**
 * agent-services chat 成功体。{@code todos} 为本轮结束时的完整快照（空列表 = 无 todo），
 * 调用方负责持久化/回传以实现跨轮编排；{@code runMeta}（用量 / 工具轨迹 / skills）仅用于观测。
 * {@code citations} 仅在本轮实际采用 grounding 时有元素。
 * {@code extractedMemories} 为本轮抽取的耐久事实；字段缺失时为空列表。
 */
public record AgentChatResponse(
        String reply,
        String conversationId,
        String providerStatus,
        List<AgentTodoItem> todos,
        AgentRunMeta runMeta,
        List<AgentCitation> citations,
        List<String> extractedMemories) {

    public AgentChatResponse {
        todos = todos == null ? List.of() : List.copyOf(todos);
        runMeta = runMeta == null ? AgentRunMeta.EMPTY : runMeta;
        citations = citations == null ? List.of() : List.copyOf(citations);
        extractedMemories = extractedMemories == null ? List.of() : List.copyOf(extractedMemories);
    }

    public AgentChatResponse(String reply, String conversationId, String providerStatus) {
        this(reply, conversationId, providerStatus, List.of(), AgentRunMeta.EMPTY, List.of(), List.of());
    }

    public AgentChatResponse(
            String reply,
            String conversationId,
            String providerStatus,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta) {
        this(reply, conversationId, providerStatus, todos, runMeta, List.of(), List.of());
    }

    public AgentChatResponse(
            String reply,
            String conversationId,
            String providerStatus,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentCitation> citations) {
        this(reply, conversationId, providerStatus, todos, runMeta, citations, List.of());
    }

    public List<String> skillsApplied() {
        return runMeta.skillsApplied();
    }
}
