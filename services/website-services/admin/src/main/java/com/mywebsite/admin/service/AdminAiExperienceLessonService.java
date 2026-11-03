package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonApproveRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonCreateRequest;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonDetailDto;
import com.mywebsite.admin.dto.AdminAiExperienceLessonDtos.AdminAiExperienceLessonListItemDto;
import java.util.List;

/**
 * 经验沉淀人审（Issue #184 UoW-3）：审核队列的读写与流转都收口在本服务，
 * 状态机校验与 L 编号分配在 agent-core {@code AgentExperienceLessonService}
 * 单一实现里，本服务只做 DTO 映射与触发提炼的转发，不另判状态。
 */
public interface AdminAiExperienceLessonService {

    /** 按状态列条目（审核队列）；status 非法时由状态机服务 BAD_REQUEST。 */
    List<AdminAiExperienceLessonListItemDto> listByStatus(String status);

    /** 单条详情（四字段全文＋来源三 id）；不存在时 NOT_FOUND。 */
    AdminAiExperienceLessonDetailDto getDetail(long id);

    /** 手动新建候选（来源三 id 留空），落 PENDING。 */
    AdminAiExperienceLessonDetailDto createCandidate(AdminAiExperienceLessonCreateRequest request);

    /** 批准（可带修订，存改写后版本并分配 L 编号）；非 PENDING 时 CONFLICT。 */
    AdminAiExperienceLessonDetailDto approve(long id, AdminAiExperienceLessonApproveRequest request);

    /** 驳回；非 PENDING 时 CONFLICT。 */
    AdminAiExperienceLessonDetailDto reject(long id);

    /** 停用已批准条目（L 编号保留）；非 APPROVED 时 CONFLICT。 */
    AdminAiExperienceLessonDetailDto disable(long id);

    /** 手动批量触发一批提炼（P1 定案），返回汇总五数。 */
    AdminAiExperienceExtractionResultDto extractRecent(AdminAiExperienceExtractionRequestDto request);
}
