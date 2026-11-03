package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionRequestDto;
import com.mywebsite.admin.dto.AdminAiExperienceExtractionDtos.AdminAiExperienceExtractionResultDto;

/**
 * 经验提炼管线（Issue #184 UoW-2）：手动批量触发（P1 定案）的服务层入口，
 * HTTP 触发端点在 UoW-3 的人审界面一并接上，本接口先行落地供其调用。
 *
 * <p>流程：信号预筛（零模型成本）→ 按来源 agent 分组组链 → 调执行层提炼 →
 * 候选逐条落 {@code agent_experience_lesson} 待审（PENDING）。
 */
public interface AdminAiExperienceExtractionService {

    /** 跑一批提炼并返回汇总（扫描轮数／提炼调用数／产出候选数／失败轮数）。 */
    AdminAiExperienceExtractionResultDto extractRecent(AdminAiExperienceExtractionRequestDto request);
}
