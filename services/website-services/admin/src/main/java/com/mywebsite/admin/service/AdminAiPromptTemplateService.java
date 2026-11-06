package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateCreateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateUpdateRequest;
import java.util.List;

/**
 * Prompt 模板与 agent Prompt 配置（Issue #214 UoW-4）：模板是平台级提示词资产，
 * 与 agent 之间只有拷贝关系（套用/另存），没有引用——改模板不影响任何已套用
 * 的 agent，删模板同样不拦截（agent 行上只留来源 id 留痕）。agent 的解析真相
 * 是行上的本地副本＋自有技能集。取代已退役的配置预设服务（#211）。
 */
public interface AdminAiPromptTemplateService {

    List<AdminAiPromptTemplateDto> listTemplates();

    AdminAiPromptTemplateDto getTemplate(long id);

    AdminAiPromptTemplateDto createTemplate(AdminAiPromptTemplateCreateRequest request);

    AdminAiPromptTemplateDto updateTemplate(long id, AdminAiPromptTemplateUpdateRequest request);

    void softDeleteTemplate(long id);

    AdminAgentPromptConfigDto getAgentPromptConfig(long agentId);

    AdminAgentPromptConfigDto updateAgentPromptConfig(
            long agentId, AdminAgentPromptConfigUpdateRequest request);

    AdminAgentPromptConfigDto loadAgentPromptFromTemplate(long agentId, long templateId);

    AdminAiPromptTemplateDto saveAgentPromptAsTemplate(long agentId, String name);
}
