package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AdminLoginRequestDto;
import com.mywebsite.auth.dto.AdminLoginResponseDto;
import com.mywebsite.auth.dto.AdminRefreshResponseDto;
import com.mywebsite.auth.dto.AdminLoginCookieDto;
import com.mywebsite.auth.dto.AdminRefreshCookieDto;
import com.mywebsite.auth.service.AdminAuthService;
import com.mywebsite.auth.service.AdminRefreshCookieService;
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
    @Operation(
            summary = "管理员登录（Refresh Token 写入 HttpOnly Cookie）",
            description =
                    "username + password 校验成功后签发 Access JWT（Body）；Refresh 不透明串写入配置的 HttpOnly Cookie（path "
                            + "限定为 Refresh 链路）。用户名不存在与密码错误均返回同一业务口径，避免账号枚举。")
    public ApiResponse<AdminLoginResponseDto> login(
            @Valid @RequestBody AdminLoginRequestDto request, HttpServletResponse response) {
        AdminLoginCookieDto dto = adminAuthService.login(request.username(), request.password());
        adminRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
        return ApiResponse.success(dto.loginResponse());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "刷新访问令牌（从 Cookie 读取 Refresh Token）",
            description =
                    "从 Refresh Cookie 读取不透明串，Redis Session 校验并执行轮换（旧 Refresh 立即失效）；"
                            + "成功签发新 Access JWT 并写入新 Refresh Cookie。Cookie 缺失、无效令牌、replay 等均返回业务 "
                            + "UNAUTHORIZED 码并由服务端清空 Refresh Cookie。")
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
    @Operation(
            summary = "管理员登出（幂等；清除服务端会话与 Refresh Cookie）",
            description =
                    "根据 Refresh Cookie 撤销 Redis Session（若缺失或已吊销则仍为成功响应）；清除 Refresh Cookie；"
                            + "不进行 Access JWT 服务端黑名单。")
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
