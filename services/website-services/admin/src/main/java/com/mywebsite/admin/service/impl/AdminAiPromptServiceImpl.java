package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileDto;
import com.mywebsite.admin.dto.AdminAiPromptDtos.AdminAiPromptProfileUpdateRequest;
import com.mywebsite.admin.entity.AdminAiPromptProfileEntity;
import com.mywebsite.admin.mapper.AdminAiPromptProfileMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiPromptServiceImpl implements AdminAiPromptService {

    private final AdminAiPromptProfileMapper promptProfileMapper;
    private final AdminSiteMapper adminSiteMapper;

    public AdminAiPromptServiceImpl(
            AdminAiPromptProfileMapper promptProfileMapper, AdminSiteMapper adminSiteMapper) {
        this.promptProfileMapper = promptProfileMapper;
        this.adminSiteMapper = adminSiteMapper;
    }

    @Override
    public AdminAiPromptProfileDto getPromptProfile(long siteId) {
        requireSite(siteId);
        AdminAiPromptProfileEntity profile = selectBySiteId(siteId);
        if (profile == null) {
            // 未配置返回空壳而非 404：前端据此渲染"尚未配置"
            return new AdminAiPromptProfileDto(null, siteId, null, null, null, null);
        }
        return toDto(profile);
    }

    @Override
    @Transactional
    public AdminAiPromptProfileDto upsertPromptProfile(
            long siteId, AdminAiPromptProfileUpdateRequest request) {
        requireSite(siteId);
        AdminAiPromptProfileEntity profile = selectBySiteId(siteId);
        if (profile == null) {
            profile = new AdminAiPromptProfileEntity();
            profile.setSiteId(siteId);
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
        return toDto(selectBySiteId(siteId));
    }

    private void requireSite(long siteId) {
        if (adminSiteMapper.selectById(siteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
    }

    private AdminAiPromptProfileEntity selectBySiteId(long siteId) {
        return promptProfileMapper.selectOne(new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                .eq(AdminAiPromptProfileEntity::getSiteId, siteId)
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
                profile.getSiteId(),
                profile.getSystemPrompt(),
                profile.getTemperature(),
                profile.getVersion(),
                profile.getUpdatedAt());
    }
}
