package com.mywebsite.agent.dto;

import java.util.List;

/**
 * Portal 双模式可用性：每种模式一条（链非空 = 已配置可用），label 是主用入口的模型名。
 * {@code entries} 是该模式生效链上的候选（含全站默认），不含 key。顺序与对话链一致。
 */
public record AgentAiModelModesDto(List<ModeOption> modes) {

    public record ModeOption(String mode, boolean available, String label, List<EntryOption> entries) {}

    public record EntryOption(Long id, String displayName, String modelSpec) {}
}
