package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 分享快照的公开读模型（与落库 snapshot JSON 同构）。只含正文与附件的计数/文件名，
 * 不含图片数据、引用与过程信息。
 */
public record AgentAiSharedConversationDto(String title, String sharedAt, List<SharedMessage> messages) {

    public record SharedMessage(
            String role, String content, String createdAt, int imageCount, List<String> documentNames) {}
}
