package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AgentLoginCookieDto;
import com.mywebsite.auth.dto.AgentLoginRequestDto;
import com.mywebsite.auth.dto.AgentLoginResponseDto;
import com.mywebsite.auth.service.AgentAuthService;
import com.mywebsite.auth.service.AgentRefreshCookieService;
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
@RequestMapping("/api/auth/agent")
@Tag(name = "Auth · Agent Login")
public class AuthAgentLoginController {

    private final AgentAuthService agentAuthService;

    private final AgentRefreshCookieService agentRefreshCookieService;

    public AuthAgentLoginController(AgentAuthService agentAuthService, AgentRefreshCookieService agentRefreshCookieService) {
        this.agentAuthService = agentAuthService;
        this.agentRefreshCookieService = agentRefreshCookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "前台站点登录（Access JWT；Refresh HttpOnly Cookie）")
    public ResponseEntity<HttpMessageBody<AgentLoginResponseDto>> login(
            @Valid @RequestBody AgentLoginRequestDto body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AgentLoginCookieDto dto = agentAuthService.login(
                body.agentCode(), body.username(), body.password(), ClientIpUtils.resolveClientIp(request));
        agentRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.loginResponse()));
    }
}
