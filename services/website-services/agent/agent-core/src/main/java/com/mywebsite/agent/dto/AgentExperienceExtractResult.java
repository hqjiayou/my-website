package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 经验提炼结果（Issue #184 UoW-2）：结构化候选（四字段草案 + 来源 turnId）、
 * 本次调用用量与实际命中的入口 spec。usage 为 null 表示执行层未回报用量，
 * 记账侧按 NULL 落账（沿 #140 口径），不以 0 冒充。
 */
public record AgentExperienceExtractResult(
        List<Candidate> candidates, AgentRunMeta.Usage usage, String modelSpec) {

    /** 一条提炼候选草案；sourceTurnId 对应请求 turns 里的 turnId。 */
    public record Candidate(
            String sourceTurnId, String title, String symptom, String lesson, String evidence) {}
}
