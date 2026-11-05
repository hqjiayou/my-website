package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetDto;
import com.mywebsite.admin.dto.AdminAiConfigPresetDtos.AdminAiConfigPresetUpdateRequest;
import com.mywebsite.admin.entity.AdminAiConfigPresetEntity;
import com.mywebsite.admin.mapper.AdminAiConfigPresetMapper;
import com.mywebsite.admin.service.AdminAiConfigPresetService;
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
public class AdminAiConfigPresetServiceImpl implements AdminAiConfigPresetService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiConfigPresetServiceImpl.class);

    private final AdminAiConfigPresetMapper configPresetMapper;
    private final ObjectMapper objectMapper;

    public AdminAiConfigPresetServiceImpl(
            AdminAiConfigPresetMapper configPresetMapper, ObjectMapper objectMapper) {
        this.configPresetMapper = configPresetMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminAiConfigPresetDto> listPresets() {
        return configPresetMapper
                .selectList(new LambdaQueryWrapper<AdminAiConfigPresetEntity>()
                        .orderByDesc(AdminAiConfigPresetEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AdminAiConfigPresetDto getPreset(long id) {
        return toDto(requirePreset(id));
    }

    @Override
    @Transactional
    public AdminAiConfigPresetDto createPreset(AdminAiConfigPresetCreateRequest request) {
        AdminAiConfigPresetEntity preset = new AdminAiConfigPresetEntity();
        preset.setName(request.name().trim());
        preset.setSystemPrompt(blankToNull(request.systemPrompt()));
        preset.setTemperature(request.temperature());
        preset.setDefaultSkillIds(writeSkillIds(request.defaultSkillIds()));
        configPresetMapper.insert(preset);
        return toDto(requirePreset(preset.getId()));
    }

    @Override
    @Transactional
    public AdminAiConfigPresetDto updatePreset(long id, AdminAiConfigPresetUpdateRequest request) {
        AdminAiConfigPresetEntity preset = requirePreset(id);
        preset.setName(request.name().trim());
        preset.setSystemPrompt(blankToNull(request.systemPrompt()));
        preset.setTemperature(request.temperature());
        preset.setDefaultSkillIds(writeSkillIds(request.defaultSkillIds()));
        preset.setVersion(request.version());
        int updated = configPresetMapper.updateById(preset);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(requirePreset(id));
    }

    @Override
    @Transactional
    public void softDeletePreset(long id) {
        AdminAiConfigPresetEntity preset = requirePreset(id);
        // 与各领域 softDeleteByAgentId 相同的逐行软删口径：deleted_at＋@TableLogic。
        // 只删预设本身；引用它的 agent 不级联，解析时按悬空引用回退（见 #211 解析口径）。
        preset.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        configPresetMapper.updateById(preset);
        configPresetMapper.deleteById(id);
    }

    private AdminAiConfigPresetEntity requirePreset(long id) {
        AdminAiConfigPresetEntity preset = configPresetMapper.selectById(id);
        if (preset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return preset;
    }

    private AdminAiConfigPresetDto toDto(AdminAiConfigPresetEntity preset) {
        return new AdminAiConfigPresetDto(
                preset.getId(),
                preset.getName(),
                preset.getSystemPrompt(),
                preset.getTemperature(),
                readSkillIds(preset),
                preset.getVersion(),
                preset.getUpdatedAt());
    }

    /** 默认集落库形态：JSON 数组文本（原序）；null/空集存 NULL，表示无预设默认集。 */
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

    /** 脏 JSON 不阻断读路径：按无默认集处理并记 warn（同会话 Todo 快照的容脏口径）。 */
    private List<String> readSkillIds(AdminAiConfigPresetEntity preset) {
        String raw = preset.getDefaultSkillIds();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return normalizeSkillIds(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("config preset {} has malformed default_skill_ids, treated as empty", preset.getId(), ex);
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
