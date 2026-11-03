package com.mywebsite.agent.service.impl;

import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentExperienceLessonRevision;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.mapper.AgentExperienceLessonMapper;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 经验沉淀状态机实现（Issue #184 UoW-1）。流转校验集中在本类：调用方（UoW-2
 * 提炼落库、UoW-3 人审端点）不各自判断状态。L 编号分配与批准在同一事务内，
 * 并有 DB 唯一索引兜底（并发批准撞号时事务回滚、由调用方重试）。
 */
@Service
public class AgentExperienceLessonServiceImpl implements AgentExperienceLessonService {

    private static final Set<String> STATUSES =
            Set.of(
                    AgentExperienceLessonEntity.STATUS_PENDING,
                    AgentExperienceLessonEntity.STATUS_APPROVED,
                    AgentExperienceLessonEntity.STATUS_REJECTED,
                    AgentExperienceLessonEntity.STATUS_DISABLED);

    private final AgentExperienceLessonMapper lessonMapper;

    public AgentExperienceLessonServiceImpl(AgentExperienceLessonMapper lessonMapper) {
        this.lessonMapper = lessonMapper;
    }

    @Override
    @Transactional
    public AgentExperienceLessonEntity createCandidate(AgentExperienceLessonDraft draft) {
        if (draft == null || !StringUtils.hasText(draft.title()) || !StringUtils.hasText(draft.lesson())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        AgentExperienceLessonEntity entity = new AgentExperienceLessonEntity();
        entity.setTitle(draft.title().trim());
        entity.setSymptom(trimToNull(draft.symptom()));
        entity.setLesson(draft.lesson().trim());
        entity.setEvidence(trimToNull(draft.evidence()));
        entity.setSourceAgentId(draft.sourceAgentId());
        entity.setSourceConversationId(draft.sourceConversationId());
        entity.setSourceMessageId(draft.sourceMessageId());
        entity.setStatus(AgentExperienceLessonEntity.STATUS_PENDING);
        entity.setLessonNo(null);
        entity.setReviewedAt(null);
        lessonMapper.insert(entity);
        return entity;
    }

    @Override
    public AgentExperienceLessonEntity getById(long id) {
        return requireLesson(id);
    }

    @Override
    @Transactional
    public AgentExperienceLessonEntity approve(long id, AgentExperienceLessonRevision revision) {
        AgentExperienceLessonEntity entity = requireLesson(id);
        requireStatus(entity, AgentExperienceLessonEntity.STATUS_PENDING);
        if (revision != null) {
            applyRevision(entity, revision);
        }
        entity.setLessonNo(nextLessonNo());
        entity.setStatus(AgentExperienceLessonEntity.STATUS_APPROVED);
        entity.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lessonMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public AgentExperienceLessonEntity reject(long id) {
        AgentExperienceLessonEntity entity = requireLesson(id);
        requireStatus(entity, AgentExperienceLessonEntity.STATUS_PENDING);
        entity.setStatus(AgentExperienceLessonEntity.STATUS_REJECTED);
        entity.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lessonMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional
    public AgentExperienceLessonEntity disable(long id) {
        AgentExperienceLessonEntity entity = requireLesson(id);
        requireStatus(entity, AgentExperienceLessonEntity.STATUS_APPROVED);
        entity.setStatus(AgentExperienceLessonEntity.STATUS_DISABLED);
        entity.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lessonMapper.updateById(entity);
        return entity;
    }

    @Override
    public List<AgentExperienceLessonEntity> listByStatus(String status) {
        if (status == null || !STATUSES.contains(status.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return lessonMapper.selectByStatus(status.trim());
    }

    @Override
    public List<AgentExperienceLessonEntity> listApproved() {
        return lessonMapper.selectApproved();
    }

    /** 人审修订：非空字段覆盖候选原文（批准存改写后的版本）；title／lesson 不允许被改成空白。 */
    private static void applyRevision(AgentExperienceLessonEntity entity, AgentExperienceLessonRevision revision) {
        if (revision.title() != null) {
            if (!StringUtils.hasText(revision.title())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            entity.setTitle(revision.title().trim());
        }
        if (revision.lesson() != null) {
            if (!StringUtils.hasText(revision.lesson())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            entity.setLesson(revision.lesson().trim());
        }
        if (revision.symptom() != null) {
            entity.setSymptom(trimToNull(revision.symptom()));
        }
        if (revision.evidence() != null) {
            entity.setEvidence(trimToNull(revision.evidence()));
        }
    }

    /** 下一个 L 编号：现有最大值 +1，且不低于 L6 起分下限；含已停用条目（编号不回收）。 */
    private int nextLessonNo() {
        Integer max = lessonMapper.selectMaxLessonNo();
        int floor = AgentExperienceLessonEntity.FIRST_LESSON_NO - 1;
        return Math.max(max == null ? floor : max, floor) + 1;
    }

    private AgentExperienceLessonEntity requireLesson(long id) {
        AgentExperienceLessonEntity entity = lessonMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private static void requireStatus(AgentExperienceLessonEntity entity, String expected) {
        if (!expected.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
