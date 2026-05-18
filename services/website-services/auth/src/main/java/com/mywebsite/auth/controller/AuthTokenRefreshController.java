package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AdminRefreshCookieDto;
import com.mywebsite.auth.dto.AdminRefreshResponseDto;
import com.mywebsite.auth.service.AdminAuthService;
import com.mywebsite.auth.service.AdminRefreshCookieService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
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
@RequestMapping("/api/auth/token")
@Tag(name = "Auth · Token")
public class AuthTokenRefreshController {

    private final AdminAuthService adminAuthService;

    private final AdminRefreshCookieService adminRefreshCookieService;

    public AuthTokenRefreshController(
            AdminAuthService adminAuthService, AdminRefreshCookieService adminRefreshCookieService) {
        this.adminAuthService = adminAuthService;
        this.adminRefreshCookieService = adminRefreshCookieService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Access Token（从 Cookie 读取 Refresh）")
    public ResponseEntity<HttpMessageBody<AdminRefreshResponseDto>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal =
                readCookie(request, adminRefreshCookieService.refreshCookieName());
        try {
            AdminRefreshCookieDto dto = adminAuthService.refresh(cookieVal);
            adminRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
            return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.refreshResponse()));
        } catch (BusinessException ex) {
            adminRefreshCookieService.clearRefreshCookie(response);
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
