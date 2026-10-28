package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileDto;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileUpdateRequest;
import com.mywebsite.admin.entity.AdminAiPromptProfileEntity;
import com.mywebsite.admin.mapper.AdminAiPromptProfileMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiPromptServiceImpl implements AdminAiPromptService {

    private final AdminAiPromptProfileMapper promptProfileMapper;
    private final AdminAgentMapper adminAgentMapper;

    public AdminAiPromptServiceImpl(
            AdminAiPromptProfileMapper promptProfileMapper, AdminAgentMapper adminAgentMapper) {
        this.promptProfileMapper = promptProfileMapper;
        this.adminAgentMapper = adminAgentMapper;
    }

    @Override
    public AdminAiPromptProfileDto getPromptProfile(long agentId) {
        requireSite(agentId);
        AdminAiPromptProfileEntity profile = selectByAgentId(agentId);
        if (profile == null) {
            // 未配置返回空壳而非 404：前端据此渲染"尚未配置"
            return new AdminAiPromptProfileDto(null, agentId, null, null, null, null);
        }
        return toDto(profile);
    }

    @Override
    @Transactional
    public void softDeleteByAgentId(long agentId) {
        List<AdminAiPromptProfileEntity> profiles = promptProfileMapper.selectList(
                new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                        .eq(AdminAiPromptProfileEntity::getAgentId, agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AdminAiPromptProfileEntity profile : profiles) {
            profile.setDeletedAt(now);
            promptProfileMapper.updateById(profile);
            promptProfileMapper.deleteById(profile.getId());
        }
    }

    @Override
    @Transactional
    public AdminAiPromptProfileDto upsertPromptProfile(
            long agentId, AdminAiPromptProfileUpdateRequest request) {
        requireSite(agentId);
        AdminAiPromptProfileEntity profile = selectByAgentId(agentId);
        if (profile == null) {
            profile = new AdminAiPromptProfileEntity();
            profile.setAgentId(agentId);
            applyRequest(profile, request);
            promptProfileMapper.insert(profile);
        } else {
            applyRequest(profile, request);
            profile.setVersion(request.version());
            int updated = promptProfileMapper.updateById(profile);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
        }
        return toDto(selectByAgentId(agentId));
    }

    private void requireSite(long agentId) {
        if (adminAgentMapper.selectById(agentId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.SITE_NOT_FOUND);
        }
    }

    private AdminAiPromptProfileEntity selectByAgentId(long agentId) {
        return promptProfileMapper.selectOne(new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                .eq(AdminAiPromptProfileEntity::getAgentId, agentId)
                .last("LIMIT 1"));
    }

    private static void applyRequest(
            AdminAiPromptProfileEntity profile, AdminAiPromptProfileUpdateRequest request) {
        profile.setSystemPrompt(
                StringUtils.hasText(request.systemPrompt()) ? request.systemPrompt().trim() : null);
        profile.setTemperature(request.temperature());
    }

    private static AdminAiPromptProfileDto toDto(AdminAiPromptProfileEntity profile) {
        return new AdminAiPromptProfileDto(
                profile.getId(),
                profile.getAgentId(),
                profile.getSystemPrompt(),
                profile.getTemperature(),
                profile.getVersion(),
                profile.getUpdatedAt());
    }
}
