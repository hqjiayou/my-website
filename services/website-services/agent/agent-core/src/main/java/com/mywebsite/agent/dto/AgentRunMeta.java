package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 单轮 Agent 运行元数据（§22.8）：用量、工具轨迹（仅工具名与成败，不含参数/返回内容）、生效 skills，
 * 以及知识搜索的安全摘要（查询词与命中数，不含片段正文）。
 * agent 侧随 ASSISTANT 消息持久化到 {@code site_ai_message.agent_meta}（JSON），admin 只读展示；
 * 其中工具名/成败与搜索摘要会对站点用户以过程摘要形式回放（与实时事件同一白名单）。
 */
public record AgentRunMeta(
        Usage usage, List<ToolCall> tools, List<String> skillsApplied, List<SearchSummary> searches) {

    public AgentRunMeta {
        usage = usage == null ? Usage.EMPTY : usage;
        tools = tools == null ? List.of() : List.copyOf(tools);
        skillsApplied = skillsApplied == null ? List.of() : List.copyOf(skillsApplied);
        searches = searches == null ? List.of() : List.copyOf(searches);
    }

    /** 兼容旧构造点与旧 JSON（无 searches 字段时反序列化走规范构造、缺省为空）。 */
    public AgentRunMeta(Usage usage, List<ToolCall> tools, List<String> skillsApplied) {
        this(usage, tools, skillsApplied, List.of());
    }

    public static final AgentRunMeta EMPTY = new AgentRunMeta(null, null, null, null);

    public record Usage(int requests, int toolCalls, int inputTokens, int outputTokens) {
        public static final Usage EMPTY = new Usage(0, 0, 0, 0);
    }

    public record ToolCall(String name, boolean ok) {}

    public record SearchSummary(String query, int hitCount) {}
}
