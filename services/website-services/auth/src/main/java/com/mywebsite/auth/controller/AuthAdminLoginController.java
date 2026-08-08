package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AdminLoginCookieDto;
import com.mywebsite.auth.dto.AdminLoginRequestDto;
import com.mywebsite.auth.dto.AdminLoginResponseDto;
import com.mywebsite.auth.service.AdminAuthService;
import com.mywebsite.auth.service.AdminRefreshCookieService;
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
@RequestMapping("/api/auth/admin")
@Tag(name = "Auth · Admin Login")
public class AuthAdminLoginController {

    private final AdminAuthService adminAuthService;

    private final AdminRefreshCookieService adminRefreshCookieService;

    public AuthAdminLoginController(
            AdminAuthService adminAuthService, AdminRefreshCookieService adminRefreshCookieService) {
        this.adminAuthService = adminAuthService;
        this.adminRefreshCookieService = adminRefreshCookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理端登录（Access JWT；Refresh HttpOnly Cookie）")
    public ResponseEntity<HttpMessageBody<AdminLoginResponseDto>> login(
            @Valid @RequestBody AdminLoginRequestDto body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AdminLoginCookieDto dto =
                adminAuthService.login(body.username(), body.password(), ClientIpUtils.resolveClientIp(request));
        adminRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.loginResponse()));
    }
}
