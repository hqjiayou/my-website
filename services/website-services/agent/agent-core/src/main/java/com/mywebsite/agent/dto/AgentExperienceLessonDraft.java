package com.mywebsite.agent.dto;

/**
 * 新建经验候选的入参（Issue #184 UoW-1）：结构化四字段（P4 定案）＋来源三 id。
 *
 * <p>{@code title} 与 {@code lesson} 必填（服务层校验）；{@code symptom}／
 * {@code evidence} 可空。来源三 id 标注候选出自哪轮对话（提炼管线 UoW-2 回填），
 * 人审页手动新建候选（UoW-3）时可全空。
 */
public record AgentExperienceLessonDraft(
        String title,
        String symptom,
        String lesson,
        String evidence,
        Long sourceAgentId,
        Long sourceConversationId,
        Long sourceMessageId) {}
