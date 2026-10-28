package com.mywebsite.agent.dto;

/** 一条检索命中。 */
public record AgentRetrievalHit(String sourceId, int chunkIndex, double score, String content) {}
