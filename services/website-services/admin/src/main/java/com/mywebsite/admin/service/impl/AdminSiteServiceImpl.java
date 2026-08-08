package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminSiteConstants;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.constant.AdminTemplateConstants;
import com.mywebsite.admin.constant.AdminTemplateMessageKeys;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteCreateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteDetailDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListItemDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListQuery;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteUpdateRequest;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.entity.AdminTemplateEntity;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.mapper.AdminTemplateMapper;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.admin.service.AdminReservationService;
import com.mywebsite.admin.service.AdminSiteService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminSiteServiceImpl implements AdminSiteService {

    private final AdminSiteMapper adminSiteMapper;
    private final AdminTemplateMapper adminTemplateMapper;
    private final AdminAppearanceService adminAppearanceService;
    private final AdminMediaService adminMediaService;
    private final AdminContentService adminContentService;
    private final AdminReservationService adminReservationService;

    public AdminSiteServiceImpl(
            AdminSiteMapper adminSiteMapper,
            AdminTemplateMapper adminTemplateMapper,
            AdminAppearanceService adminAppearanceService,
            AdminMediaService adminMediaService,
            AdminContentService adminContentService,
            AdminReservationService adminReservationService) {
        this.adminSiteMapper = adminSiteMapper;
        this.adminTemplateMapper = adminTemplateMapper;
        this.adminAppearanceService = adminAppearanceService;
        this.adminMediaService = adminMediaService;
        this.adminContentService = adminContentService;
        this.adminReservationService = adminReservationService;
    }

    @Override
    public PageResult<AdminSiteListItemDto> listSites(AdminSiteListQuery query) {
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminSiteConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminSiteConstants.MAX_PAGE_SIZE)
                : AdminSiteConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminSiteEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.status())) {
                requireStatus(query.status());
                wrapper.eq(AdminSiteEntity::getStatus, query.status());
            }
            if (StringUtils.hasText(query.siteType())) {
                requireSiteType(query.siteType());
                wrapper.eq(AdminSiteEntity::getSiteType, query.siteType());
            }
        }
        wrapper.orderByDesc(AdminSiteEntity::getId);

        Page<AdminSiteEntity> page =
                adminSiteMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminSiteDetailDto getSite(long id) {
        return toDetail(requireSite(id));
    }

    @Override
    @Transactional
    public AdminSiteDetailDto createSite(AdminSiteCreateRequest request) {
        if (!request.siteCode().matches(AdminSiteConstants.SITE_CODE_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_CODE_INVALID);
        }
        requireSiteType(request.siteType());
        boolean hasTemplate = request.templateId() != null;
        boolean hasAppearance = request.appearance() != null;
        if (hasTemplate != hasAppearance) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_TEMPLATE_APPEARANCE_PAIR);
        }
        if (hasTemplate) {
            requireEnabledTemplateMatchingSiteType(request.templateId(), request.siteType());
            adminAppearanceService.requireValidConfigV1(request.appearance());
        }
        if (findBySiteCode(request.siteCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminSiteMessageKeys.SITE_CODE_DUPLICATE);
        }

        AdminSiteEntity entity = new AdminSiteEntity();
        entity.setSiteCode(request.siteCode());
        entity.setSiteName(request.siteName());
        entity.setSiteType(request.siteType());
        entity.setDefaultDomain(blankToNull(request.defaultDomain()));
        entity.setDescription(blankToNull(request.description()));
        entity.setStatus(AdminSiteConstants.STATUS_ACTIVE);
        entity.setTemplateId(request.templateId());
        try {
            adminSiteMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminSiteMessageKeys.SITE_CODE_DUPLICATE);
        }
        if (hasAppearance) {
            adminAppearanceService.insertForSite(entity.getId(), request.appearance());
        }
        return toDetail(requireSite(entity.getId()));
    }

    @Override
    public AdminSiteDetailDto updateSite(long id, AdminSiteUpdateRequest request) {
        AdminSiteEntity site = requireSite(id);
        site.setSiteName(request.siteName());
        if (StringUtils.hasText(request.siteType())) {
            requireSiteType(request.siteType());
            site.setSiteType(request.siteType());
        }
        site.setDefaultDomain(blankToNull(request.defaultDomain()));
        site.setDescription(blankToNull(request.description()));
        site.setVersion(request.version());
        int updated = adminSiteMapper.updateById(site);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminSiteMessageKeys.SITE_VERSION_CONFLICT);
        }
        return toDetail(requireSite(id));
    }

    @Override
    @Transactional
    public void deleteSite(long id) {
        requireSite(id);
        adminAppearanceService.softDeleteBySiteId(id);
        adminContentService.softDeleteBySiteId(id);
        adminReservationService.softDeleteBySiteId(id);
        adminMediaService.softDeleteBySiteId(id);
        int deleted = adminSiteMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
    }

    @Override
    public AdminSiteDetailDto updateStatus(long id, AdminSiteStatusUpdateRequest request) {
        requireStatus(request.status());
        AdminSiteEntity site = requireSite(id);
        site.setStatus(request.status());
        site.setVersion(request.version());
        int updated = adminSiteMapper.updateById(site);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminSiteMessageKeys.SITE_VERSION_CONFLICT);
        }
        return toDetail(requireSite(id));
    }

    private void requireEnabledTemplateMatchingSiteType(long templateId, String siteType) {
        AdminTemplateEntity template = adminTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminTemplateMessageKeys.TEMPLATE_NOT_FOUND);
        }
        if (!AdminTemplateConstants.STATUS_ENABLED.equals(template.getStatus())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_TEMPLATE_NOT_ENABLED);
        }
        if (!siteType.equals(template.getSiteType())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_TEMPLATE_SITE_TYPE_MISMATCH);
        }
    }

    private AdminSiteEntity requireSite(long id) {
        AdminSiteEntity site = adminSiteMapper.selectById(id);
        if (site == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
        }
        return site;
    }

    /** 按 site_code 查未逻辑删除行（{@code @TableLogic}）；不按 ACTIVE 过滤，用于唯一性校验。 */
    private AdminSiteEntity findBySiteCode(String siteCode) {
        return adminSiteMapper.selectOne(
                new LambdaQueryWrapper<AdminSiteEntity>().eq(AdminSiteEntity::getSiteCode, siteCode));
    }

    private static void requireSiteType(String siteType) {
        if (!AdminSiteConstants.ALLOWED_SITE_TYPES.contains(siteType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_TYPE_INVALID);
        }
    }

    private static void requireStatus(String status) {
        if (!AdminSiteConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminSiteMessageKeys.SITE_STATUS_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private AdminSiteListItemDto toListItem(AdminSiteEntity entity) {
        return new AdminSiteListItemDto(
                entity.getId(),
                entity.getSiteCode(),
                entity.getSiteName(),
                entity.getSiteType(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminSiteDetailDto toDetail(AdminSiteEntity entity) {
        return new AdminSiteDetailDto(
                entity.getId(),
                entity.getSiteCode(),
                entity.getSiteName(),
                entity.getSiteType(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getDescription(),
                entity.getTemplateId(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
