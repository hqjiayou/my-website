package com.mywebsite.agent.dto;

import java.time.OffsetDateTime;

/** 站点用户只读浏览知识库文档（列表不含正文，详情含 Markdown 正文）。 */
public final class AgentKnowledgeDocumentDtos {

    private AgentKnowledgeDocumentDtos() {}

    public record ListItem(Long id, String title, OffsetDateTime updatedAt) {}

    public record Detail(Long id, String title, String content, OffsetDateTime updatedAt) {}
}
