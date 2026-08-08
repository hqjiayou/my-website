package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminAppearanceConstants;
import com.mywebsite.admin.constant.AdminAppearanceMessageKeys;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceConfigV1Dto;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearanceDetailDto;
import com.mywebsite.admin.dto.AdminAppearanceDtos.AdminAppearancePutRequest;
import com.mywebsite.admin.entity.AdminAppearanceEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminAppearanceMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAppearanceServiceImpl implements AdminAppearanceService {

    private final AdminAppearanceMapper adminAppearanceMapper;
    private final AdminSiteMapper adminSiteMapper;
    private final ObjectMapper objectMapper;

    public AdminAppearanceServiceImpl(
            AdminAppearanceMapper adminAppearanceMapper,
            AdminSiteMapper adminSiteMapper,
            ObjectMapper objectMapper) {
        this.adminAppearanceMapper = adminAppearanceMapper;
        this.adminSiteMapper = adminSiteMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AdminAppearanceDetailDto getAppearance(long siteId) {
        requireSite(siteId);
        return toDetail(requireAppearance(siteId));
    }

    @Override
    @Transactional
    public AdminAppearanceDetailDto putAppearance(long siteId, AdminAppearancePutRequest request) {
        requireSite(siteId);
        requireSchemaVersionV1(request.schemaVersion());
        requireValidConfigV1(request.config());

        AdminAppearanceEntity existing = findBySiteId(siteId);
        if (existing == null) {
            if (request.version() == null || request.version() != 0) {
                throw new BusinessException(
                        ErrorCode.CONFLICT.getCode(), AdminAppearanceMessageKeys.APPEARANCE_VERSION_CONFLICT);
            }
            insertForSite(siteId, request.config());
            return toDetail(requireAppearance(siteId));
        }

        existing.setSchemaVersion(AdminAppearanceConstants.SCHEMA_VERSION_V1);
        existing.setConfigJson(writeConfigJson(request.config()));
        existing.setVersion(request.version());
        int updated = adminAppearanceMapper.updateById(existing);
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAppearanceMessageKeys.APPEARANCE_VERSION_CONFLICT);
        }
        return toDetail(requireAppearance(siteId));
    }

    @Override
    public void insertForSite(long siteId, AdminAppearanceConfigV1Dto config) {
        requireValidConfigV1(config);
        AdminAppearanceEntity entity = new AdminAppearanceEntity();
        entity.setSiteId(siteId);
        entity.setSchemaVersion(AdminAppearanceConstants.SCHEMA_VERSION_V1);
        entity.setConfigJson(writeConfigJson(config));
        adminAppearanceMapper.insert(entity);
    }

    @Override
    public void softDeleteBySiteId(long siteId) {
        AdminAppearanceEntity existing = findBySiteId(siteId);
        if (existing != null) {
            adminAppearanceMapper.deleteById(existing.getId());
        }
    }

    @Override
    public void requireValidConfigV1(AdminAppearanceConfigV1Dto config) {
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAppearanceMessageKeys.APPEARANCE_CONFIG_INVALID);
        }
        if (config.primaryColor() == null
                || !config.primaryColor().matches(AdminAppearanceConstants.PRIMARY_COLOR_PATTERN)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAppearanceMessageKeys.APPEARANCE_CONFIG_INVALID);
        }
        if (!AdminAppearanceConstants.FONT_STYLES.contains(config.fontStyle())
                || !AdminAppearanceConstants.BUTTON_STYLES.contains(config.buttonStyle())
                || !AdminAppearanceConstants.HERO_LAYOUTS.contains(config.heroLayout())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAppearanceMessageKeys.APPEARANCE_CONFIG_INVALID);
        }
    }

    @Override
    public void requireSchemaVersionV1(Integer schemaVersion) {
        if (schemaVersion == null || schemaVersion != AdminAppearanceConstants.SCHEMA_VERSION_V1) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAppearanceMessageKeys.APPEARANCE_SCHEMA_INVALID);
        }
    }

    private AdminSiteEntity requireSite(long siteId) {
        AdminSiteEntity site = adminSiteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
        return site;
    }

    private AdminAppearanceEntity requireAppearance(long siteId) {
        AdminAppearanceEntity entity = findBySiteId(siteId);
        if (entity == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAppearanceMessageKeys.APPEARANCE_NOT_FOUND);
        }
        return entity;
    }

    private AdminAppearanceEntity findBySiteId(long siteId) {
        return adminAppearanceMapper.selectOne(
                new LambdaQueryWrapper<AdminAppearanceEntity>()
                        .eq(AdminAppearanceEntity::getSiteId, siteId));
    }

    private AdminAppearanceDetailDto toDetail(AdminAppearanceEntity entity) {
        return new AdminAppearanceDetailDto(
                entity.getSchemaVersion(),
                readConfigJson(entity.getConfigJson()),
                entity.getVersion(),
                entity.getUpdatedAt());
    }

    private String writeConfigJson(AdminAppearanceConfigV1Dto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminAppearanceMessageKeys.APPEARANCE_CONFIG_INVALID);
        }
    }

    private AdminAppearanceConfigV1Dto readConfigJson(String json) {
        try {
            return objectMapper.readValue(json, AdminAppearanceConfigV1Dto.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR.getCode(), AdminAppearanceMessageKeys.APPEARANCE_CONFIG_INVALID);
        }
    }
}
