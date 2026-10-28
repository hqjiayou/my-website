package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 多轮历史消息；role 为 USER 或 ASSISTANT（与 Python ChatHistoryMessage 契约一致）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentChatHistoryMessage(String role, String content, List<AgentAttachmentDto> attachments) {

    public AgentChatHistoryMessage(String role, String content) {
        this(role, content, null);
    }
}
