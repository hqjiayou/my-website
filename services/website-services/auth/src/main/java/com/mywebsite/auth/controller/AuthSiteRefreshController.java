package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.SiteRefreshCookieDto;
import com.mywebsite.auth.dto.SiteRefreshResponseDto;
import com.mywebsite.auth.service.SiteAuthService;
import com.mywebsite.auth.service.SiteRefreshCookieService;
import com.mywebsite.auth.utils.ClientIpUtils;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/site")
@Tag(name = "Auth · Site Refresh")
public class AuthSiteRefreshController {

    private final SiteAuthService siteAuthService;

    private final SiteRefreshCookieService siteRefreshCookieService;

    public AuthSiteRefreshController(
            SiteAuthService siteAuthService, SiteRefreshCookieService siteRefreshCookieService) {
        this.siteAuthService = siteAuthService;
        this.siteRefreshCookieService = siteRefreshCookieService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Access Token（从 Cookie 读取 Site Refresh）")
    public ResponseEntity<HttpMessageBody<SiteRefreshResponseDto>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal = readCookie(request, siteRefreshCookieService.refreshCookieName());
        try {
            SiteRefreshCookieDto dto =
                    siteAuthService.refresh(cookieVal, ClientIpUtils.resolveClientIp(request));
            siteRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
            return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.refreshResponse()));
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.UNAUTHORIZED.getCode()) {
                siteRefreshCookieService.clearRefreshCookie(response);
            }
            throw ex;
        }
    }

    private static Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue()).filter(v -> !v.isBlank());
            }
        }
        return Optional.empty();
    }
}
