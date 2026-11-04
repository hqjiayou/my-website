package com.mywebsite.agent.dto;

import java.util.List;

/**
 * MCP per-agent 选择的解析结果（Issue #192 UoW-3）：{@code mode} 为
 * INHERIT／OFF／CUSTOM；{@code serverNames} 仅 CUSTOM 时非空（已去重、保序），
 * 其余态为空列表。无选择行时等价 {@code (INHERIT, [])}。
 */
public record AgentMcpSelection(String mode, List<String> serverNames) {

    public static AgentMcpSelection inherit() {
        return new AgentMcpSelection("INHERIT", List.of());
    }

    /**
     * 组装下发口径：INHERIT → null（字段缺席、不限制）；OFF → 空列表
     * （本 agent 不用 MCP）；CUSTOM → 名字列表（执行层与全局启用集取交集）。
     */
    public List<String> wireNames() {
        return switch (mode) {
            case "OFF" -> List.of();
            case "CUSTOM" -> serverNames;
            default -> null;
        };
    }
}
