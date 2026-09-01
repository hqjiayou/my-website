package com.mywebsite.site.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationCreateRequest;
import com.mywebsite.site.dto.SiteInternalDtos.InternalReservationDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteContentListDto;
import com.mywebsite.site.dto.SiteInternalDtos.SiteSummaryDto;
import com.mywebsite.site.service.SiteInternalService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * agent-services 业务工具回调（internal only）。
 * 鉴权由 {@link com.mywebsite.site.security.InternalServiceTokenFilter} 承担，不走 JWT。
 */
@Hidden
@RestController
@RequestMapping("/internal/site/v1/sites/{siteId}")
public class SiteInternalController {

    private final SiteInternalService siteInternalService;

    public SiteInternalController(SiteInternalService siteInternalService) {
        this.siteInternalService = siteInternalService;
    }

    @GetMapping
    public ApiResponse<SiteSummaryDto> getSiteSummary(@PathVariable("siteId") long siteId) {
        return ApiResponse.success(siteInternalService.getSiteSummary(siteId));
    }

    @GetMapping("/contents")
    public ApiResponse<SiteContentListDto> listPublishedContents(
            @PathVariable("siteId") long siteId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        return ApiResponse.success(siteInternalService.listPublishedContents(siteId, type, limit));
    }

    @PostMapping("/reservations")
    public ApiResponse<InternalReservationDto> createReservation(
            @PathVariable("siteId") long siteId,
            @Valid @RequestBody InternalReservationCreateRequest request) {
        return ApiResponse.success(siteInternalService.createReservation(siteId, request));
    }
}
