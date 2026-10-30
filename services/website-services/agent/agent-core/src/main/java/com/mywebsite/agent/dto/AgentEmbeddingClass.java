package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 一类嵌入模型的凭证。分类键是 {@code embeddingSpec}，与 Python {@code EmbeddingClassCredentials} 同形。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEmbeddingClass(String embeddingSpec, String apiKey, String baseUrl) {}
