package com.mywebsite.site.dto;

import java.util.List;

/** agent-services 业务工具回调专用 DTO（internal API，非公开契约）。 */
public final class SiteInternalDtos {

    private SiteInternalDtos() {}

    public record SiteSummaryDto(Long id, String siteName, String siteType, String description) {}

    public record SiteContentItemDto(Long id, String type, String title, String body) {}

    public record SiteContentListDto(List<SiteContentItemDto> items) {}
}
