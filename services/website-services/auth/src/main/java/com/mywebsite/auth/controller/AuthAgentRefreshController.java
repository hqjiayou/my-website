package com.mywebsite.auth.controller;

import com.mywebsite.auth.dto.AgentRefreshCookieDto;
import com.mywebsite.auth.dto.AgentRefreshResponseDto;
import com.mywebsite.auth.service.AgentAuthService;
import com.mywebsite.auth.service.AgentRefreshCookieService;
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
@RequestMapping("/api/auth/agent")
@Tag(name = "Auth · Agent Refresh")
public class AuthAgentRefreshController {

    private final AgentAuthService agentAuthService;

    private final AgentRefreshCookieService agentRefreshCookieService;

    public AuthAgentRefreshController(
            AgentAuthService agentAuthService, AgentRefreshCookieService agentRefreshCookieService) {
        this.agentAuthService = agentAuthService;
        this.agentRefreshCookieService = agentRefreshCookieService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Access Token（从 Cookie 读取 Agent Refresh）")
    public ResponseEntity<HttpMessageBody<AgentRefreshResponseDto>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal = readCookie(request, agentRefreshCookieService.refreshCookieName());
        try {
            AgentRefreshCookieDto dto =
                    agentAuthService.refresh(cookieVal, ClientIpUtils.resolveClientIp(request));
            agentRefreshCookieService.writeRefreshCookie(response, dto.refreshPlain(), dto.refreshExpiresAt());
            return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, dto.refreshResponse()));
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.UNAUTHORIZED.getCode()) {
                agentRefreshCookieService.clearRefreshCookie(response);
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
