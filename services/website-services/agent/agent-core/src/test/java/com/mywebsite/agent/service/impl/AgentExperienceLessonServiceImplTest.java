package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentExperienceLessonRevision;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.mapper.AgentExperienceLessonMapper;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 经验沉淀状态机测试（Issue #184 UoW-1）：以内存假 Mapper 承接 insert/select/update，
 * 验流转合法性、L 编号分配与人审修订落库，不依赖真实 DB（迁移本体由 PG 契约测试覆盖）。
 */
class AgentExperienceLessonServiceImplTest {

    private final Map<Long, AgentExperienceLessonEntity> store = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(100);
    private AgentExperienceLessonService service;

    @BeforeEach
    void setUp() {
        AgentExperienceLessonMapper mapper = mock(AgentExperienceLessonMapper.class);
        when(mapper.insert(any(AgentExperienceLessonEntity.class)))
                .thenAnswer(
                        inv -> {
                            AgentExperienceLessonEntity entity = inv.getArgument(0);
                            entity.setId(ids.incrementAndGet());
                            store.put(entity.getId(), entity);
                            return 1;
                        });
        when(mapper.selectById(anyLong())).thenAnswer(inv -> store.get(inv.getArgument(0, Long.class)));
        when(mapper.updateById(any(AgentExperienceLessonEntity.class)))
                .thenAnswer(
                        inv -> {
                            AgentExperienceLessonEntity entity = inv.getArgument(0);
                            store.put(entity.getId(), entity);
                            return 1;
                        });
        when(mapper.selectMaxLessonNo())
                .thenAnswer(
                        inv ->
                                store.values().stream()
                                        .map(AgentExperienceLessonEntity::getLessonNo)
                                        .filter(java.util.Objects::nonNull)
                                        .max(Comparator.naturalOrder())
                                        .orElse(null));
        when(mapper.selectByStatus(any()))
                .thenAnswer(
                        inv ->
                                store.values().stream()
                                        .filter(e -> inv.getArgument(0, String.class).equals(e.getStatus()))
                                        .sorted(Comparator.comparing(AgentExperienceLessonEntity::getId))
                                        .toList());
        when(mapper.selectApproved())
                .thenAnswer(
                        inv ->
                                store.values().stream()
                                        .filter(
                                                e ->
                                                        AgentExperienceLessonEntity.STATUS_APPROVED.equals(
                                                                e.getStatus()))
                                        .sorted(
                                                Comparator.comparing(
                                                        AgentExperienceLessonEntity::getLessonNo))
                                        .toList());
        service = new AgentExperienceLessonServiceImpl(mapper);
    }

    private AgentExperienceLessonEntity createPending() {
        return service.createCandidate(
                new AgentExperienceLessonDraft(
                        "预算撞顶先查枚举", "症状文本", "集合级问题先枚举再逐份抽取", "证据文本", 7L, 42L, 9001L));
    }

    @Test
    void createCandidateStoresPendingWithoutLessonNo() {
        AgentExperienceLessonEntity entity = createPending();

        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getStatus()).isEqualTo(AgentExperienceLessonEntity.STATUS_PENDING);
        assertThat(entity.getLessonNo()).isNull();
        assertThat(entity.getReviewedAt()).isNull();
        assertThat(entity.getTitle()).isEqualTo("预算撞顶先查枚举");
        assertThat(entity.getLesson()).isEqualTo("集合级问题先枚举再逐份抽取");
        assertThat(entity.getSourceAgentId()).isEqualTo(7L);
        assertThat(entity.getSourceConversationId()).isEqualTo(42L);
        assertThat(entity.getSourceMessageId()).isEqualTo(9001L);
        assertThat(service.listByStatus(AgentExperienceLessonEntity.STATUS_PENDING)).containsExactly(entity);
    }

    @Test
    void createCandidateRejectsBlankTitleOrLesson() {
        assertThatThrownBy(
                        () ->
                                service.createCandidate(
                                        new AgentExperienceLessonDraft(" ", null, "规则", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThatThrownBy(
                        () ->
                                service.createCandidate(
                                        new AgentExperienceLessonDraft("标题", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    void approveAssignsLessonNumbersFromSixAndMonotonically() {
        AgentExperienceLessonEntity first = createPending();
        AgentExperienceLessonEntity second = createPending();

        service.approve(first.getId(), AgentExperienceLessonRevision.none());
        service.approve(second.getId(), null);

        assertThat(first.getStatus()).isEqualTo(AgentExperienceLessonEntity.STATUS_APPROVED);
        assertThat(first.getLessonNo()).isEqualTo(6);
        assertThat(first.getReviewedAt()).isNotNull();
        assertThat(second.getLessonNo()).isEqualTo(7);
        assertThat(service.listApproved()).containsExactly(first, second);
    }

    @Test
    void approveAppliesRevisionAndStoresRewrittenText() {
        AgentExperienceLessonEntity entity = createPending();

        service.approve(
                entity.getId(),
                new AgentExperienceLessonRevision(null, null, "人审改写后的规则文本", null));

        assertThat(entity.getLesson()).isEqualTo("人审改写后的规则文本");
        assertThat(entity.getTitle()).isEqualTo("预算撞顶先查枚举");
        assertThat(store.get(entity.getId()).getLesson()).isEqualTo("人审改写后的规则文本");
    }

    @Test
    void approveRejectsRevisionThatBlanksLesson() {
        AgentExperienceLessonEntity entity = createPending();

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        entity.getId(),
                                        new AgentExperienceLessonRevision(null, null, "  ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        assertThat(entity.getStatus()).isEqualTo(AgentExperienceLessonEntity.STATUS_PENDING);
    }

    @Test
    void rejectMovesPendingToRejectedWithoutLessonNo() {
        AgentExperienceLessonEntity entity = createPending();

        service.reject(entity.getId());

        assertThat(entity.getStatus()).isEqualTo(AgentExperienceLessonEntity.STATUS_REJECTED);
        assertThat(entity.getLessonNo()).isNull();
        assertThat(entity.getReviewedAt()).isNotNull();
    }

    @Test
    void disableMovesApprovedToDisabledAndKeepsLessonNo() {
        AgentExperienceLessonEntity entity = createPending();
        service.approve(entity.getId(), null);

        service.disable(entity.getId());

        assertThat(entity.getStatus()).isEqualTo(AgentExperienceLessonEntity.STATUS_DISABLED);
        assertThat(entity.getLessonNo()).isEqualTo(6);
        assertThat(service.listApproved()).isEmpty();
    }

    @Test
    void lessonNumbersAreNotReusedAfterDisable() {
        AgentExperienceLessonEntity first = createPending();
        service.approve(first.getId(), null);
        service.disable(first.getId());
        AgentExperienceLessonEntity second = createPending();

        service.approve(second.getId(), null);

        assertThat(second.getLessonNo()).isEqualTo(7);
    }

    @Test
    void illegalTransitionsAreRejectedWithConflict() {
        AgentExperienceLessonEntity rejected = createPending();
        service.reject(rejected.getId());
        // REJECTED 再批准 / 再驳回
        assertConflict(() -> service.approve(rejected.getId(), null));
        assertConflict(() -> service.reject(rejected.getId()));
        // REJECTED 停用
        assertConflict(() -> service.disable(rejected.getId()));

        AgentExperienceLessonEntity pending = createPending();
        // PENDING 直接停用
        assertConflict(() -> service.disable(pending.getId()));

        AgentExperienceLessonEntity approved = createPending();
        service.approve(approved.getId(), null);
        // APPROVED 再批准 / 再驳回
        assertConflict(() -> service.approve(approved.getId(), null));
        assertConflict(() -> service.reject(approved.getId()));

        service.disable(approved.getId());
        // DISABLED 再停用 / 再批准
        assertConflict(() -> service.disable(approved.getId()));
        assertConflict(() -> service.approve(approved.getId(), null));
    }

    @Test
    void missingLessonIsNotFound() {
        assertThatThrownBy(() -> service.approve(999999L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void getByIdReturnsStoredLessonAndRejectsMissing() {
        AgentExperienceLessonEntity created =
                service.createCandidate(new AgentExperienceLessonDraft("标题", "症状", "教训", "证据", 7L, 8L, 9L));

        assertThat(service.getById(created.getId()).getTitle()).isEqualTo("标题");
        assertThatThrownBy(() -> service.getById(999999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void listByStatusRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.listByStatus("WHATEVER"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT.getCode());
    }
}
