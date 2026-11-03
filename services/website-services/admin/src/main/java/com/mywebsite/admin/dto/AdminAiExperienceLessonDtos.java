package com.mywebsite.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class AdminAiExperienceLessonDtos {

    private AdminAiExperienceLessonDtos() {}

    /**
     * 审核列表行（Issue #184 UoW-3）：只带列表渲染需要的字段，lesson 正文截断为摘要；
     * 全文走详情。来源只回表里实存的 sourceAgentId，不臆造信号类型（提炼信号在
     * agent_meta 里、条目表不落，列表不假装有）。
     */
    public record AdminAiExperienceLessonListItemDto(
            long id,
            String title,
            String lessonSummary,
            Long sourceAgentId,
            String status,
            Integer lessonNo,
            OffsetDateTime createdAt,
            OffsetDateTime reviewedAt) {}

    /** 条目详情：四字段全文＋来源三 id＋状态机字段，人审展开与动作响应共用。 */
    public record AdminAiExperienceLessonDetailDto(
            long id,
            String title,
            String symptom,
            String lesson,
            String evidence,
            Long sourceAgentId,
            Long sourceConversationId,
            Long sourceMessageId,
            String status,
            Integer lessonNo,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime reviewedAt) {}

    /**
     * 手动新建候选（兜底试聊等未落库场景）：title／lesson 必填，与服务层
     * createCandidate 的校验口径一致；来源三 id 由端点固定留空，不接受前端传。
     */
    public record AdminAiExperienceLessonCreateRequest(
            @NotBlank @Size(max = 256) String title,
            @Size(max = 20000) String symptom,
            @NotBlank @Size(max = 20000) String lesson,
            @Size(max = 20000) String evidence) {}

    /**
     * 批准时的可选修订：四字段逐项可改，null＝不改、沿用候选原文（与 agent-core
     * AgentExperienceLessonRevision 语义一致，原样透传）；title／lesson 被改成
     * 空白时由服务层 BAD_REQUEST 拒绝。
     */
    public record AdminAiExperienceLessonApproveRequest(
            @Size(max = 256) String title,
            @Size(max = 20000) String symptom,
            @Size(max = 20000) String lesson,
            @Size(max = 20000) String evidence) {}
}
