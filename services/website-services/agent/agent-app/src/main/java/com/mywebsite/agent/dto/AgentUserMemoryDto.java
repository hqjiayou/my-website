package com.mywebsite.agent.dto;

import java.time.OffsetDateTime;

public record AgentUserMemoryDto(Long id, String content, OffsetDateTime createdAt) {}
