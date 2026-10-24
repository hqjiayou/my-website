package com.mywebsite.site.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 匿名公开只读。列表不含 body。 */
public final class SitePublicDtos {

    private SitePublicDtos() {}

    public record SitePublicSiteDto(String siteCode, String siteName, String siteType, String description) {}

    public record SitePublicContentItemDto(
            Long id, String type, String title, Long coverMediaId, OffsetDateTime publishedAt) {}

    public record SitePublicContentListDto(List<SitePublicContentItemDto> items) {}

    public record SitePublicContentDetailDto(
            Long id, String type, String title, String body, Long coverMediaId, OffsetDateTime publishedAt) {}
}
