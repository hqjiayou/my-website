package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/**
 * 经验沉淀条目（Issue #184）：候选从正式对话提炼后落本表待审，人审批准后经请求注入
 * 在对话组装时生效。DB 为唯一真相源；harness/EXPERIENCE.md 只留 L1–L5 历史存档。
 *
 * <p>实体与 Mapper 放 agent-core 共享：admin 侧写入/审核（UoW-2/3）、agent-app 侧
 * 读取已批准条目下发（UoW-4），与 {@link AgentUserMemoryEntity}、
 * {@code AgentAiUsageEntity} 的落点一致，避免同表双份映射。
 */
@TableName("agent_experience_lesson")
public class AgentExperienceLessonEntity extends AbstractAuditableEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** L 编号下限：L1–L5 是 EXPERIENCE.md 历史存档，本表条目从 L6 起分配。 */
    public static final int FIRST_LESSON_NO = 6;

    private String title;
    private String symptom;
    private String lesson;
    private String evidence;
    private Long sourceAgentId;
    private Long sourceConversationId;
    private Long sourceMessageId;
    private String status;
    private Integer lessonNo;
    private OffsetDateTime reviewedAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSymptom() {
        return symptom;
    }

    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }

    public String getLesson() {
        return lesson;
    }

    public void setLesson(String lesson) {
        this.lesson = lesson;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public Long getSourceAgentId() {
        return sourceAgentId;
    }

    public void setSourceAgentId(Long sourceAgentId) {
        this.sourceAgentId = sourceAgentId;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public Long getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(Long sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLessonNo() {
        return lessonNo;
    }

    public void setLessonNo(Integer lessonNo) {
        this.lessonNo = lessonNo;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
