package com.mywebsite.agent.dto;

/** 知识检索请求。{@code topK} 为 null 时由 agent-services 使用默认条数。 */
public record AgentRetrievalSearchRequest(String siteId, String query, Integer topK) {}
