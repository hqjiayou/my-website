package com.mywebsite.auth.controller;

import com.mywebsite.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth Probe", description = "探活")
@RestController
@RequestMapping("/api/auth")
public class AuthPingController {

    @Operation(summary = "Ping", description = "验证 Auth 服务已启动；响应 message 为多语言框架下的 success key。")
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of("service", "auth-service"));
    }
}
