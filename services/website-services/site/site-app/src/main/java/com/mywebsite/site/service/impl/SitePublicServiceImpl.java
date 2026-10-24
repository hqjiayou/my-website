package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentDetailDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentItemDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentListDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicSiteDto;
import com.mywebsite.site.entity.SiteContentEntity;
import com.mywebsite.site.entity.SiteEntity;
import com.mywebsite.site.mapper.SiteContentMapper;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.service.SitePublicService;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SitePublicServiceImpl implements SitePublicService {

    /**
     * 与 {@code SiteAuthServiceImpl} 登录解析站点相同：siteCode trim 后精确匹配；
     * status trim 后转大写，等于 ACTIVE。不存在或非 ACTIVE 一律 NOT_FOUND。
     */
    private static final String ACTIVE_SITE_STATUS = "ACTIVE";

    private final SiteMapper siteMapper;
    private final SiteContentMapper siteContentMapper;

    public SitePublicServiceImpl(SiteMapper siteMapper, SiteContentMapper siteContentMapper) {
        this.siteMapper = siteMapper;
        this.siteContentMapper = siteContentMapper;
    }

    @Override
    public SitePublicSiteDto getSite(String siteCode) {
        SiteEntity site = requireActiveSite(siteCode);
        return new SitePublicSiteDto(
                site.getSiteCode(), site.getSiteName(), site.getSiteType(), site.getDescription());
    }

    @Override
    public SitePublicContentListDto listContents(String siteCode) {
        SiteEntity site = requireActiveSite(siteCode);
        // 与 SiteInternalServiceImpl.listPublishedContents、AdminContentServiceImpl.listContents 相同。
        LambdaQueryWrapper<SiteContentEntity> wrapper = new LambdaQueryWrapper<SiteContentEntity>()
                .eq(SiteContentEntity::getSiteId, site.getId())
                .eq(SiteContentEntity::getStatus, SiteContentEntity.STATUS_PUBLISHED)
                .orderByAsc(SiteContentEntity::getSortOrder)
                .orderByDesc(SiteContentEntity::getId);
        return new SitePublicContentListDto(siteContentMapper.selectList(wrapper).stream()
                .filter(content -> isPublishedOnSite(content, site.getId()))
                .map(SitePublicServiceImpl::toItem)
                .toList());
    }

    @Override
    public SitePublicContentDetailDto getContent(String siteCode, long contentId) {
        SiteEntity site = requireActiveSite(siteCode);
        if (contentId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        SiteContentEntity content = siteContentMapper.selectOne(new LambdaQueryWrapper<SiteContentEntity>()
                .eq(SiteContentEntity::getId, contentId)
                .eq(SiteContentEntity::getSiteId, site.getId())
                .eq(SiteContentEntity::getStatus, SiteContentEntity.STATUS_PUBLISHED));
        if (!isPublishedOnSite(content, site.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toDetail(content);
    }

    private SiteEntity requireActiveSite(String siteCode) {
        if (!StringUtils.hasText(siteCode)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        SiteEntity site = siteMapper.selectOne(
                new LambdaQueryWrapper<SiteEntity>().eq(SiteEntity::getSiteCode, siteCode.trim()));
        if (!isActive(site)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return site;
    }

    private static boolean isActive(SiteEntity site) {
        if (site == null || site.getId() == null) {
            return false;
        }
        String status = site.getStatus() == null ? "" : site.getStatus().trim().toUpperCase(Locale.ROOT);
        return ACTIVE_SITE_STATUS.equals(status);
    }

    /** 查询已限定 PUBLISHED 与 siteId；这里再挡一层，避免未发布或跨站行进入公开响应。 */
    private static boolean isPublishedOnSite(SiteContentEntity content, Long siteId) {
        return content != null
                && siteId != null
                && siteId.equals(content.getSiteId())
                && SiteContentEntity.STATUS_PUBLISHED.equals(content.getStatus());
    }

    private static SitePublicContentItemDto toItem(SiteContentEntity content) {
        return new SitePublicContentItemDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getCoverMediaId(),
                content.getPublishedAt());
    }

    private static SitePublicContentDetailDto toDetail(SiteContentEntity content) {
        return new SitePublicContentDetailDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getBody(),
                content.getCoverMediaId(),
                content.getPublishedAt());
    }
}
