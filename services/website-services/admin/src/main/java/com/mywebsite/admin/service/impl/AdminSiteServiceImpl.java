package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminSiteConstants;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteCreateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteDetailDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListItemDto;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteListQuery;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminSiteDtos.AdminSiteUpdateRequest;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.admin.service.AdminSiteService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminSiteServiceImpl implements AdminSiteService {

    private final AdminSiteMapper adminSiteMapper;
    private final AdminAppearanceService adminAppearanceService;
    private final AdminMediaService adminMediaService;
    private final AdminContentService adminContentService;
    private final AdminAiPromptService adminAiPromptService;
    private final AdminAiKnowledgeService adminAiKnowledgeService;
    private final AdminAiConversationService adminAiConversationService;

    public AdminSiteServiceImpl(
            AdminSiteMapper adminSiteMapper,
            AdminAppearanceService adminAppearanceService,
            AdminMediaService adminMediaService,
            AdminContentService adminContentService,
            AdminAiPromptService adminAiPromptService,
            AdminAiKnowledgeService adminAiKnowledgeService,
            AdminAiConversationService adminAiConversationService) {
        this.adminSiteMapper = adminSiteMapper;
        this.adminAppearanceService = adminAppearanceService;
        this.adminMediaService = adminMediaService;
        this.adminContentService = adminContentService;
        this.adminAiPromptService = adminAiPromptService;
        this.adminAiKnowledgeService = adminAiKnowledgeService;
        this.adminAiConversationService = adminAiConversationService;
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
        boolean hasAppearance = request.appearance() != null;
        if (hasAppearance) {
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
        AdminSiteEntity site = requireSite(id);
        adminAppearanceService.softDeleteBySiteId(id);
        adminContentService.softDeleteBySiteId(id);
        adminMediaService.softDeleteBySiteId(id);
        adminAiPromptService.softDeleteBySiteId(id);
        adminAiKnowledgeService.softDeleteBySiteId(id);
        adminAiConversationService.softDeleteBySiteId(id);
        site.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminSiteMapper.updateById(site);
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

    /** 用途标签校验：非空且符合标签模式（预设标签与自订标签同等对待），不再受旧行业闭集约束。 */
    private static void requireSiteType(String siteType) {
        if (!StringUtils.hasText(siteType) || !siteType.trim().matches(AdminSiteConstants.SITE_TAG_PATTERN)) {
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
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
