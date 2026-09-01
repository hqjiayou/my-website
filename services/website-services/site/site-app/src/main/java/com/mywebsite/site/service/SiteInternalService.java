package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationCreateRequest;
import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentListDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteSummaryDto;

/** agent-services 业务工具回调（Phase 4）；业务真相源保持在 Java。 */
public interface SiteInternalService {

    SiteSummaryDto getSiteSummary(long siteId);

    SiteContentListDto listPublishedContents(long siteId, String type, int limit);

    InternalReservationDto createReservation(long siteId, InternalReservationCreateRequest request);
}
