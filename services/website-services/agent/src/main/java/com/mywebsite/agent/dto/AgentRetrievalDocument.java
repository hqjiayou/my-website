package com.mywebsite.agent.dto;

/** KB 文档派生索引同步载荷；sourceId 建议为 {@code kb:<documentId>}，同键覆盖。 */
public record AgentRetrievalDocument(String siteId, String sourceId, String title, String content) {}
