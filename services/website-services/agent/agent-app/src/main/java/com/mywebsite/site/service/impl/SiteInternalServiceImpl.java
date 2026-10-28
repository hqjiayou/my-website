package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentItemDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentListDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteSummaryDto;
import com.mywebsite.site.entity.SiteContentEntity;
import com.mywebsite.site.entity.SiteEntity;
import com.mywebsite.site.mapper.SiteContentMapper;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.service.SiteInternalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SiteInternalServiceImpl implements SiteInternalService {

    private static final int MAX_CONTENT_LIMIT = 50;

    private final SiteMapper siteMapper;
    private final SiteContentMapper siteContentMapper;

    public SiteInternalServiceImpl(SiteMapper siteMapper, SiteContentMapper siteContentMapper) {
        this.siteMapper = siteMapper;
        this.siteContentMapper = siteContentMapper;
    }

    @Override
    public SiteSummaryDto getSiteSummary(long siteId) {
        SiteEntity site = requireSite(siteId);
        return new SiteSummaryDto(site.getId(), site.getSiteName(), site.getSiteType(), site.getDescription());
    }

    @Override
    public SiteContentListDto listPublishedContents(long siteId, String type, int limit) {
        requireSite(siteId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_CONTENT_LIMIT);
        LambdaQueryWrapper<SiteContentEntity> wrapper = new LambdaQueryWrapper<SiteContentEntity>()
                .eq(SiteContentEntity::getSiteId, siteId)
                .eq(SiteContentEntity::getStatus, SiteContentEntity.STATUS_PUBLISHED)
                .orderByAsc(SiteContentEntity::getSortOrder)
                .orderByDesc(SiteContentEntity::getId)
                .last("LIMIT " + effectiveLimit);
        if (StringUtils.hasText(type)) {
            wrapper.eq(SiteContentEntity::getType, type.trim());
        }
        return new SiteContentListDto(siteContentMapper.selectList(wrapper).stream()
                .map(c -> new SiteContentItemDto(c.getId(), c.getType(), c.getTitle(), c.getBody()))
                .toList());
    }

    private SiteEntity requireSite(long siteId) {
        SiteEntity site = siteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return site;
    }
}
