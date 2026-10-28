package com.mywebsite.agent.dto;

import java.util.List;

/** Portal 双模式可用性：每种模式一条（链非空 = 已配置可用），label 是主用入口的模型名。 */
public record AgentAiModelModesDto(List<ModeOption> modes) {

    public record ModeOption(String mode, boolean available, String label) {}
}
