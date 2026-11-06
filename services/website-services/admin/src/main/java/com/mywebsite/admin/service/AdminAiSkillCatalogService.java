package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiSkillCatalogDtos.AdminAiSkillCatalogDto;

public interface AdminAiSkillCatalogService {

    /** 平台技能目录全量快照；agent-services 不可用时抛 EXTERNAL_DEPENDENCY_BLOCKED / SYSTEM_ERROR。 */
    AdminAiSkillCatalogDto getCatalog();
}
