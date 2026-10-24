package com.mywebsite.site.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentDetailDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicContentListDto;
import com.mywebsite.site.dto.SitePublicDtos.SitePublicSiteDto;
import com.mywebsite.site.service.SitePublicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site/public")
public class SitePublicController {

    private final SitePublicService sitePublicService;

    public SitePublicController(SitePublicService sitePublicService) {
        this.sitePublicService = sitePublicService;
    }

    @GetMapping("/{siteCode}")
    public ApiResponse<SitePublicSiteDto> getSite(@PathVariable("siteCode") String siteCode) {
        return ApiResponse.success(sitePublicService.getSite(siteCode));
    }

    @GetMapping("/{siteCode}/contents")
    public ApiResponse<SitePublicContentListDto> listContents(@PathVariable("siteCode") String siteCode) {
        return ApiResponse.success(sitePublicService.listContents(siteCode));
    }

    @GetMapping("/{siteCode}/contents/{contentId}")
    public ApiResponse<SitePublicContentDetailDto> getContent(
            @PathVariable("siteCode") String siteCode, @PathVariable("contentId") long contentId) {
        return ApiResponse.success(sitePublicService.getContent(siteCode, contentId));
    }
}
