package com.mywebsite.site.controller;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.site.dto.SiteAuthMeResponseDto;
import com.mywebsite.site.service.SiteAuthMeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site/auth")
@Tag(name = "Site · Auth Context")
public class SiteAuthMeController {

    private final SiteAuthMeService siteAuthMeService;

    public SiteAuthMeController(SiteAuthMeService siteAuthMeService) {
        this.siteAuthMeService = siteAuthMeService;
    }

    @GetMapping("/me")
    @Operation(summary = "当前前台用户上下文")
    public ResponseEntity<HttpMessageBody<SiteAuthMeResponseDto>> me() {
        SiteAuthMeResponseDto data = siteAuthMeService.loadCurrentSiteUserContext();
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
