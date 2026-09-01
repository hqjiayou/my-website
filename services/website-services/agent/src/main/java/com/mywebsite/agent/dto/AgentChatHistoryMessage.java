package com.mywebsite.agent.dto;

/** 多轮历史消息；role 为 USER 或 ASSISTANT（与 Python ChatHistoryMessage 契约一致）。 */
public record AgentChatHistoryMessage(String role, String content) {}
