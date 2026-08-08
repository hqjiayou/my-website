package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AdminRegisterRequestDto;
import com.mywebsite.auth.dto.AdminRegisterResponseDto;
import com.mywebsite.auth.service.AdminAuthService;
import com.mywebsite.auth.utils.ClientIpUtils;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/admin")
@Tag(name = "Auth · Admin Register")
public class AuthAdminRegisterController {

    private final AdminAuthService adminAuthService;

    public AuthAdminRegisterController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "管理端注册（首账号引导 / 受限开放注册）",
            description =
                    "默认仅在「尚无活跃管理员」时允许注册首账号（bootstrap）；"
                            + "生产请在配置中关闭开放注册：`security.auth.admin-open-registration-enabled`。详见接口说明与安全配置。")
    public ResponseEntity<HttpMessageBody<AdminRegisterResponseDto>> register(
            @Valid @RequestBody AdminRegisterRequestDto body, HttpServletRequest request) {
        AdminRegisterResponseDto responseBody =
                adminAuthService.register(body, ClientIpUtils.resolveClientIp(request));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, responseBody));
    }
}
