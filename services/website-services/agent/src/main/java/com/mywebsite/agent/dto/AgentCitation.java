package com.mywebsite.agent.dto;

/** 对话实际采用的检索引用。未 grounding 时响应里可以没有该列表。 */
public record AgentCitation(String sourceId, int chunkIndex, double score) {}
