package com.mywebsite.site.service;

import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentDetailDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentListDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicSiteDto;

public interface SitePublicService {

    SitePublicSiteDto getSite(String siteCode);

    SitePublicContentListDto listContents(String siteCode);

    SitePublicContentDetailDto getContent(String siteCode, long contentId);
}
