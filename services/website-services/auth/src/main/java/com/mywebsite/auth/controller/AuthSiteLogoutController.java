package com.mywebsite.auth.controller;

import com.mywebsite.auth.service.SiteAuthService;
import com.mywebsite.auth.service.SiteRefreshCookieService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.web.HttpRequestCorrelation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/site")
@Tag(name = "Auth · Site Logout")
public class AuthSiteLogoutController {

    private static final Logger log = LoggerFactory.getLogger(AuthSiteLogoutController.class);

    private final SiteAuthService siteAuthService;

    private final SiteRefreshCookieService siteRefreshCookieService;

    public AuthSiteLogoutController(
            SiteAuthService siteAuthService, SiteRefreshCookieService siteRefreshCookieService) {
        this.siteAuthService = siteAuthService;
        this.siteRefreshCookieService = siteRefreshCookieService;
    }

    @PostMapping("/logout")
    @Operation(summary = "登出（吊销 Site Refresh 会话并清除 Cookie）")
    public ResponseEntity<HttpMessageBody<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal = readCookie(request, siteRefreshCookieService.refreshCookieName());
        siteAuthService.logout(cookieVal);
        siteRefreshCookieService.clearRefreshCookie(response);
        log.info(
                "auth security logout_success requestId={} realm=site cookiePresent={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                cookieVal.isPresent());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
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
