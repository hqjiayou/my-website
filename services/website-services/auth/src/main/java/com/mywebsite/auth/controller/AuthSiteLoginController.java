package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.SiteLoginCookieDto;
import com.mywebsite.auth.dto.SiteLoginRequestDto;
import com.mywebsite.auth.dto.SiteLoginResponseDto;
import com.mywebsite.auth.service.SiteAuthService;
import com.mywebsite.auth.service.SiteRefreshCookieService;
import com.mywebsite.auth.utils.ClientIpUtils;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/site")
@Tag(name = "Auth · Site Login")
public class AuthSiteLoginController {

    private final SiteAuthService siteAuthService;

    private final SiteRefreshCookieService siteRefreshCookieService;

    public AuthSiteLoginController(SiteAuthService siteAuthService, SiteRefreshCookieService siteRefreshCookieService) {
        this.siteAuthService = siteAuthService;
        this.siteRefreshCookieService = siteRefreshCookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "前台站点登录（Access JWT；Refresh HttpOnly Cookie）")
    public ResponseEntity<HttpMessageBody<SiteLoginResponseDto>> login(
            @Valid @RequestBody SiteLoginRequestDto body,
            HttpServletRequest request,
            HttpServletResponse response) {
        SiteLoginCookieDto dto = siteAuthService.login(
                body.siteCode(), body.username(), body.password(), ClientIpUtils.resolveClientIp(request));
        siteRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.loginResponse()));
    }
}
