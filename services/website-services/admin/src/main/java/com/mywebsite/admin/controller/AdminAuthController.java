package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminLoginCookieDto;
import com.mywebsite.admin.dto.AdminLoginRequestDto;
import com.mywebsite.admin.dto.AdminLoginResponseDto;
import com.mywebsite.admin.dto.AdminRefreshCookieDto;
import com.mywebsite.admin.dto.AdminRefreshResponseDto;
import com.mywebsite.admin.service.AdminAuthService;
import com.mywebsite.admin.service.AdminRefreshCookieService;
import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin Auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    private final AdminRefreshCookieService adminRefreshCookieService;

    public AdminAuthController(AdminAuthService adminAuthService, AdminRefreshCookieService adminRefreshCookieService) {
        this.adminAuthService = adminAuthService;
        this.adminRefreshCookieService = adminRefreshCookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录（Refresh Token 写入 HttpOnly Cookie）")
    public ApiResponse<AdminLoginResponseDto> login(
            @Valid @RequestBody AdminLoginRequestDto request, HttpServletResponse response) {
        AdminLoginCookieDto dto = adminAuthService.login(request.username(), request.password());
        adminRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
        return ApiResponse.success(dto.loginResponse());
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌（从 Cookie 读取 Refresh Token）")
    public ApiResponse<AdminRefreshResponseDto> refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal =
                readCookie(request, adminRefreshCookieService.refreshCookieName());
        try {
            AdminRefreshCookieDto dto = adminAuthService.refresh(cookieVal);
            adminRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
            return ApiResponse.success(dto.refreshResponse());
        } catch (BusinessException ex) {
            adminRefreshCookieService.clearRefreshCookie(response);
            throw ex;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员登出（幂等；清除服务端会话与 Refresh Cookie）")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal =
                readCookie(request, adminRefreshCookieService.refreshCookieName());
        adminAuthService.logout(cookieVal);
        adminRefreshCookieService.clearRefreshCookie(response);
        return ApiResponse.success();
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
