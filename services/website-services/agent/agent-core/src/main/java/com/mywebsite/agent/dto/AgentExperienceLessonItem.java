package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 已批准经验条目的下发形状（Issue #184 UoW-4）：随 {@link AgentChatRequest} 下发，
 * Python 组装为 {@code # Experience lessons} 段。只带 L 编号与 lesson 规则文本；
 * title／symptom／evidence 是人审材料，不入 prompt。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExperienceLessonItem(Integer lessonNo, String lesson) {}
