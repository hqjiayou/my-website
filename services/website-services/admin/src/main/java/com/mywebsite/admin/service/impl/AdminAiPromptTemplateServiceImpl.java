package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAgentPromptConfigUpdateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateCreateRequest;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateDto;
import com.mywebsite.admin.dto.AdminAiPromptTemplateDtos.AdminAiPromptTemplateUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiPromptTemplateEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiPromptTemplateMapper;
import com.mywebsite.admin.service.AdminAiPromptTemplateService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiPromptTemplateServiceImpl implements AdminAiPromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiPromptTemplateServiceImpl.class);

    private final AdminAiPromptTemplateMapper promptTemplateMapper;
    private final AdminAgentMapper agentMapper;
    private final ObjectMapper objectMapper;

    public AdminAiPromptTemplateServiceImpl(
            AdminAiPromptTemplateMapper promptTemplateMapper,
            AdminAgentMapper agentMapper,
            ObjectMapper objectMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.agentMapper = agentMapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Prompt 模板库
    // ------------------------------------------------------------------

    @Override
    public List<AdminAiPromptTemplateDto> listTemplates() {
        return promptTemplateMapper
                .selectList(new LambdaQueryWrapper<AdminAiPromptTemplateEntity>()
                        .orderByDesc(AdminAiPromptTemplateEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AdminAiPromptTemplateDto getTemplate(long id) {
        return toDto(requireTemplate(id));
    }

    @Override
    @Transactional
    public AdminAiPromptTemplateDto createTemplate(AdminAiPromptTemplateCreateRequest request) {
        AdminAiPromptTemplateEntity template = new AdminAiPromptTemplateEntity();
        template.setName(request.name().trim());
        template.setSystemPrompt(blankToNull(request.systemPrompt()));
        template.setTemperature(request.temperature());
        promptTemplateMapper.insert(template);
        return toDto(requireTemplate(template.getId()));
    }

    @Override
    @Transactional
    public AdminAiPromptTemplateDto updateTemplate(long id, AdminAiPromptTemplateUpdateRequest request) {
        AdminAiPromptTemplateEntity template = requireTemplate(id);
        template.setName(request.name().trim());
        template.setSystemPrompt(blankToNull(request.systemPrompt()));
        template.setTemperature(request.temperature());
        template.setVersion(request.version());
        int updated = promptTemplateMapper.updateById(template);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(requireTemplate(id));
    }

    @Override
    @Transactional
    public void softDeleteTemplate(long id) {
        AdminAiPromptTemplateEntity template = requireTemplate(id);
        // 模板与 agent 只有拷贝关系：只删模板本身；已套用 agent 的本地副本与
        // 留痕 id 都不动（留痕解析不到名字时管理面按 id 照实展示）。
        template.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        promptTemplateMapper.updateById(template);
        promptTemplateMapper.deleteById(id);
    }

    // ------------------------------------------------------------------
    // agent Prompt 配置（本地副本＋自有技能集）
    // ------------------------------------------------------------------

    @Override
    public AdminAgentPromptConfigDto getAgentPromptConfig(long agentId) {
        return toAgentPromptConfigDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAgentPromptConfigDto updateAgentPromptConfig(
            long agentId, AdminAgentPromptConfigUpdateRequest request) {
        AdminAgentEntity agent = requireAgent(agentId);
        // 整表保存本地副本：空白 prompt 落 NULL（＝不带提示词，与未配置同形态）；
        // 技能集规范化后空集落 NULL（＝无自有集，解析回退 env 兜底）。
        // 来源留痕不随手改：它只在套用/另存时刷新。
        agent.setPromptSystemPrompt(blankToNull(request.systemPrompt()));
        agent.setPromptTemperature(request.temperature());
        agent.setDefaultSkillIds(writeSkillIds(request.defaultSkillIds()));
        int updated = agentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toAgentPromptConfigDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAgentPromptConfigDto loadAgentPromptFromTemplate(long agentId, long templateId) {
        AdminAgentEntity agent = requireAgent(agentId);
        AdminAiPromptTemplateEntity template = requireTemplate(templateId);
        // 拷贝语义：模板的 prompt 部分逐字拷入本地副本并刷新留痕，写入后与模板
        // 脱钩；自有技能集不随模板动（模板本就不带技能集）。
        agent.setPromptSystemPrompt(template.getSystemPrompt());
        agent.setPromptTemperature(template.getTemperature());
        agent.setPromptTemplateId(template.getId());
        int updated = agentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toAgentPromptConfigDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAiPromptTemplateDto saveAgentPromptAsTemplate(long agentId, String name) {
        AdminAgentEntity agent = requireAgent(agentId);
        AdminAiPromptTemplateEntity template = new AdminAiPromptTemplateEntity();
        template.setName(name.trim());
        template.setSystemPrompt(agent.getPromptSystemPrompt());
        template.setTemperature(agent.getPromptTemperature());
        promptTemplateMapper.insert(template);
        // 另存后留痕指向新模板：此刻本地副本与该模板内容逐字一致，出处属实。
        agent.setPromptTemplateId(template.getId());
        agentMapper.updateById(agent);
        return toDto(requireTemplate(template.getId()));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private AdminAiPromptTemplateEntity requireTemplate(long id) {
        AdminAiPromptTemplateEntity template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return template;
    }

    private AdminAgentEntity requireAgent(long agentId) {
        AdminAgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return agent;
    }

    private AdminAiPromptTemplateDto toDto(AdminAiPromptTemplateEntity template) {
        return new AdminAiPromptTemplateDto(
                template.getId(),
                template.getName(),
                template.getSystemPrompt(),
                template.getTemperature(),
                template.getVersion(),
                template.getUpdatedAt());
    }

    private AdminAgentPromptConfigDto toAgentPromptConfigDto(AdminAgentEntity agent) {
        String templateName = null;
        if (agent.getPromptTemplateId() != null) {
            AdminAiPromptTemplateEntity template =
                    promptTemplateMapper.selectById(agent.getPromptTemplateId());
            templateName = template == null ? null : template.getName();
        }
        return new AdminAgentPromptConfigDto(
                agent.getId(),
                agent.getPromptSystemPrompt(),
                agent.getPromptTemperature(),
                agent.getPromptTemplateId(),
                templateName,
                readSkillIds(agent));
    }

    /** 自有集落库形态：JSON 数组文本（原序）；null/空集存 NULL，表示无自有集。 */
    private String writeSkillIds(List<String> skillIds) {
        List<String> normalized = normalizeSkillIds(skillIds);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    /** 脏 JSON 不阻断读路径：按无自有集处理并记 warn（同会话 Todo 快照的容脏口径）。 */
    private List<String> readSkillIds(AdminAgentEntity agent) {
        String raw = agent.getDefaultSkillIds();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return normalizeSkillIds(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("agent {} has malformed default_skill_ids, treated as empty", agent.getId(), ex);
            return List.of();
        }
    }

    /** 保序、去空白与重复（与试聊 normalizeSkills 同口径）；未知 id 不在此过滤，政策点在 Python。 */
    private static List<String> normalizeSkillIds(List<String> skillIds) {
        if (skillIds == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String skillId : skillIds) {
            if (!StringUtils.hasText(skillId)) {
                continue;
            }
            String id = skillId.trim();
            if (!normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
