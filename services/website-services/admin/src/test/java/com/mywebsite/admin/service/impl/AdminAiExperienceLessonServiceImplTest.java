package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonApproveRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonCreateRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonDetailDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonListItemDto;
import com.mywebsite.admin.service.AdminAiExperienceExtractionService;
import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentExperienceLessonRevision;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 人审服务映射层测试（Issue #184 UoW-3）：验 DTO 映射（列表摘要截断、详情全字段）、
 * 手动新建的来源留空、批准修订的原样透传与业务异常不吞。端点与权限由
 * AdminAiExperienceLessonMvcTest 覆盖（沙箱 JWKS 回环受限时以 CI 为准）。
 */
class AdminAiExperienceLessonServiceImplTest {

    private AgentExperienceLessonService lessonService;
    private AdminAiExperienceExtractionService extractionService;
    private AdminAiExperienceLessonServiceImpl service;

    @BeforeEach
    void setUp() {
        lessonService = mock(AgentExperienceLessonService.class);
        extractionService = mock(AdminAiExperienceExtractionService.class);
        service = new AdminAiExperienceLessonServiceImpl(lessonService, extractionService);
    }

    private static AgentExperienceLessonEntity entity(long id, String lesson) {
        AgentExperienceLessonEntity entity = new AgentExperienceLessonEntity();
        entity.setId(id);
        entity.setTitle("标题" + id);
        entity.setSymptom("症状");
        entity.setLesson(lesson);
        entity.setEvidence("证据");
        entity.setSourceAgentId(7L);
        entity.setSourceConversationId(8L);
        entity.setSourceMessageId(9L);
        entity.setStatus(AgentExperienceLessonEntity.STATUS_PENDING);
        entity.setCreatedAt(OffsetDateTime.parse("2026-11-03T00:00:00Z"));
        entity.setUpdatedAt(OffsetDateTime.parse("2026-11-03T01:00:00Z"));
        return entity;
    }

    @Test
    void listMapsItemsAndTruncatesLongLessonSummary() {
        String longLesson = "x".repeat(250);
        when(lessonService.listByStatus("PENDING")).thenReturn(List.of(entity(1L, longLesson), entity(2L, "短教训")));

        List<AdminAiExperienceLessonListItemDto> items = service.listByStatus("PENDING");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).lessonSummary()).hasSize(201).endsWith("…");
        assertThat(items.get(1).lessonSummary()).isEqualTo("短教训");
        assertThat(items.get(0).sourceAgentId()).isEqualTo(7L);
        assertThat(items.get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void detailMapsAllFieldsIncludingSources() {
        when(lessonService.getById(3L)).thenReturn(entity(3L, "全文教训"));

        AdminAiExperienceLessonDetailDto detail = service.getDetail(3L);

        assertThat(detail.lesson()).isEqualTo("全文教训");
        assertThat(detail.sourceAgentId()).isEqualTo(7L);
        assertThat(detail.sourceConversationId()).isEqualTo(8L);
        assertThat(detail.sourceMessageId()).isEqualTo(9L);
        assertThat(detail.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-03T01:00:00Z"));
    }

    @Test
    void createPassesDraftWithNullSources() {
        when(lessonService.createCandidate(any())).thenReturn(entity(5L, "教训"));
        ArgumentCaptor<AgentExperienceLessonDraft> captor = ArgumentCaptor.forClass(AgentExperienceLessonDraft.class);

        service.createCandidate(new AdminAiExperienceLessonCreateRequest("标题", "症状", "教训", "证据"));

        verify(lessonService).createCandidate(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("标题");
        assertThat(captor.getValue().sourceAgentId()).isNull();
        assertThat(captor.getValue().sourceConversationId()).isNull();
        assertThat(captor.getValue().sourceMessageId()).isNull();
    }

    @Test
    void approvePassesRevisionThroughAndNullRequestMeansNoRevision() {
        when(lessonService.approve(eq(6L), any())).thenReturn(entity(6L, "改写后"));
        ArgumentCaptor<AgentExperienceLessonRevision> captor =
                ArgumentCaptor.forClass(AgentExperienceLessonRevision.class);

        service.approve(6L, new AdminAiExperienceLessonApproveRequest(null, null, "改写后", null));
        service.approve(6L, null);

        verify(lessonService, org.mockito.Mockito.times(2)).approve(eq(6L), captor.capture());
        assertThat(captor.getAllValues().get(0).lesson()).isEqualTo("改写后");
        assertThat(captor.getAllValues().get(0).title()).isNull();
        assertThat(captor.getAllValues().get(1)).isEqualTo(AgentExperienceLessonRevision.none());
    }

    @Test
    void businessExceptionsPropagateUnchanged() {
        when(lessonService.reject(9L)).thenThrow(new BusinessException(ErrorCode.CONFLICT));

        assertThatThrownBy(() -> service.reject(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT.getCode());
    }

    @Test
    void extractForwardsToExtractionService() {
        AdminAiExperienceExtractionRequestDto request = new AdminAiExperienceExtractionRequestDto(7, 20);
        AdminAiExperienceExtractionResultDto summary = new AdminAiExperienceExtractionResultDto(7, 4, 1, 2, 0);
        when(extractionService.extractRecent(request)).thenReturn(summary);

        assertThat(service.extractRecent(request)).isEqualTo(summary);
    }
}
