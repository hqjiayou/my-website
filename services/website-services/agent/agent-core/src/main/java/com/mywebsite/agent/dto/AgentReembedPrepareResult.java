package com.mywebsite.agent.dto;

/** 探针成功。{@code columnDimensions} 在表不存在时为 null。 */
public record AgentReembedPrepareResult(int dimensions, Integer columnDimensions, boolean recreated) {}
