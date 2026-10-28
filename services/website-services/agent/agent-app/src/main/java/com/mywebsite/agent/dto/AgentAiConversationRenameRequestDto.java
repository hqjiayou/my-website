package com.mywebsite.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重命名会话。长度上限沿用标题生成截断（{@code AgentAiConversationServiceImpl} 首条用户消息），不是列宽 VARCHAR(255)。
 */
public record AgentAiConversationRenameRequestDto(@NotBlank @Size(max = 60) String title) {

    /** 与 {@code @Size(max = 60)} 以及会话标题生成截断一致。 */
    public static final int TITLE_MAX_LENGTH = 60;
}
