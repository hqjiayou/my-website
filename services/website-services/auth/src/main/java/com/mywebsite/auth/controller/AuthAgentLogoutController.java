package com.mywebsite.auth.controller;

import com.mywebsite.auth.service.AgentAuthService;
import com.mywebsite.auth.service.AgentRefreshCookieService;
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
@RequestMapping("/api/auth/agent")
@Tag(name = "Auth · Agent Logout")
public class AuthAgentLogoutController {

    private static final Logger log = LoggerFactory.getLogger(AuthAgentLogoutController.class);

    private final AgentAuthService agentAuthService;

    private final AgentRefreshCookieService agentRefreshCookieService;

    public AuthAgentLogoutController(
            AgentAuthService agentAuthService, AgentRefreshCookieService agentRefreshCookieService) {
        this.agentAuthService = agentAuthService;
        this.agentRefreshCookieService = agentRefreshCookieService;
    }

    @PostMapping("/logout")
    @Operation(summary = "登出（吊销 Agent Refresh 会话并清除 Cookie）")
    public ResponseEntity<HttpMessageBody<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> cookieVal = readCookie(request, agentRefreshCookieService.refreshCookieName());
        agentAuthService.logout(cookieVal);
        agentRefreshCookieService.clearRefreshCookie(response);
        log.info(
                "auth security logout_success requestId={} realm=agent cookiePresent={}",
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
