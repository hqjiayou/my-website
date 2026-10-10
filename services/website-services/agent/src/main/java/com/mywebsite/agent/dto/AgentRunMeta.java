package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 单轮 Agent 运行元数据（§22.8）：用量、工具轨迹（仅工具名与成败，不含参数/返回内容）、生效 skills。
 * site 侧随 ASSISTANT 消息持久化到 {@code site_ai_message.agent_meta}（JSON），admin 只读展示；不对终端用户暴露。
 */
public record AgentRunMeta(Usage usage, List<ToolCall> tools, List<String> skillsApplied) {

    public AgentRunMeta {
        usage = usage == null ? Usage.EMPTY : usage;
        tools = tools == null ? List.of() : List.copyOf(tools);
        skillsApplied = skillsApplied == null ? List.of() : List.copyOf(skillsApplied);
    }

    public static final AgentRunMeta EMPTY = new AgentRunMeta(null, null, null);

    public record Usage(int requests, int toolCalls, int inputTokens, int outputTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0, 0);
    }

    public record ToolCall(String name, boolean ok) {}
}
