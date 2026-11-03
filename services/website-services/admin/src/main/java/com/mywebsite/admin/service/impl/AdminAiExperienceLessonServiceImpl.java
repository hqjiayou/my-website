package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonApproveRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonCreateRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonDetailDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonListItemDto;
import com.mywebsite.admin.service.AdminAiExperienceExtractionService;
import com.mywebsite.admin.service.AdminAiExperienceLessonService;
import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentExperienceLessonRevision;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 人审服务实现（Issue #184 UoW-3）：纯映射层——流转合法性、必填校验与 L 编号
 * 分配全部由 agent-core 的状态机服务裁决，本类不预判、不吞异常，业务异常
 * （CONFLICT／NOT_FOUND／BAD_REQUEST）原样上抛走 admin 统一错误形状。
 */
@Service
public class AdminAiExperienceLessonServiceImpl implements AdminAiExperienceLessonService {

    /** 列表摘要截断长度：超出截断并加省略号，全文走详情端点。 */
    private static final int LESSON_SUMMARY_MAX = 200;

    private final AgentExperienceLessonService lessonService;
    private final AdminAiExperienceExtractionService extractionService;

    public AdminAiExperienceLessonServiceImpl(
            AgentExperienceLessonService lessonService, AdminAiExperienceExtractionService extractionService) {
        this.lessonService = lessonService;
        this.extractionService = extractionService;
    }

    @Override
    public List<AdminAiExperienceLessonListItemDto> listByStatus(String status) {
        return lessonService.listByStatus(status).stream()
                .map(AdminAiExperienceLessonServiceImpl::toListItem)
                .toList();
    }

    @Override
    public AdminAiExperienceLessonDetailDto getDetail(long id) {
        return toDetail(lessonService.getById(id));
    }

    @Override
    public AdminAiExperienceLessonDetailDto createCandidate(AdminAiExperienceLessonCreateRequest request) {
        AgentExperienceLessonDraft draft =
                new AgentExperienceLessonDraft(
                        request.title(), request.symptom(), request.lesson(), request.evidence(), null, null, null);
        return toDetail(lessonService.createCandidate(draft));
    }

    @Override
    public AdminAiExperienceLessonDetailDto approve(long id, AdminAiExperienceLessonApproveRequest request) {
        AgentExperienceLessonRevision revision =
                request == null
                        ? AgentExperienceLessonRevision.none()
                        : new AgentExperienceLessonRevision(
                                request.title(), request.symptom(), request.lesson(), request.evidence());
        return toDetail(lessonService.approve(id, revision));
    }

    @Override
    public AdminAiExperienceLessonDetailDto reject(long id) {
        return toDetail(lessonService.reject(id));
    }

    @Override
    public AdminAiExperienceLessonDetailDto disable(long id) {
        return toDetail(lessonService.disable(id));
    }

    @Override
    public AdminAiExperienceExtractionResultDto extractRecent(AdminAiExperienceExtractionRequestDto request) {
        return extractionService.extractRecent(request);
    }

    private static AdminAiExperienceLessonListItemDto toListItem(AgentExperienceLessonEntity entity) {
        return new AdminAiExperienceLessonListItemDto(
                entity.getId(),
                entity.getTitle(),
                summarize(entity.getLesson()),
                entity.getSourceAgentId(),
                entity.getStatus(),
                entity.getLessonNo(),
                entity.getCreatedAt(),
                entity.getReviewedAt());
    }

    private static AdminAiExperienceLessonDetailDto toDetail(AgentExperienceLessonEntity entity) {
        return new AdminAiExperienceLessonDetailDto(
                entity.getId(),
                entity.getTitle(),
                entity.getSymptom(),
                entity.getLesson(),
                entity.getEvidence(),
                entity.getSourceAgentId(),
                entity.getSourceConversationId(),
                entity.getSourceMessageId(),
                entity.getStatus(),
                entity.getLessonNo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getReviewedAt());
    }

    private static String summarize(String lesson) {
        if (lesson == null || lesson.length() <= LESSON_SUMMARY_MAX) {
            return lesson;
        }
        return lesson.substring(0, LESSON_SUMMARY_MAX) + "…";
    }
}
