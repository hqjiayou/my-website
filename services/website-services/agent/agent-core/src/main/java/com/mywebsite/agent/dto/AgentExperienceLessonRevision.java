package com.mywebsite.agent.dto;

/**
 * 批准时的人审修订（Issue #184 UoW-1，界面在 UoW-3）：四字段逐项可改，
 * {@code null} 表示该字段不改、沿用候选原文；非空（含纯空白按清空处理前先 trim）
 * 则以修订值落库——批准存的是改写后的版本，不是候选原文。
 */
public record AgentExperienceLessonRevision(String title, String symptom, String lesson, String evidence) {

    /** 无修订批准：人审未改任何字段时用。 */
    public static AgentExperienceLessonRevision none() {
        return new AgentExperienceLessonRevision(null, null, null, null);
    }
}
