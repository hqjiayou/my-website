package com.mywebsite.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;

/** 根据配置的 Cookie 属性写入或清除 Agent Refresh HttpOnly Cookie。 */
public interface AgentRefreshCookieService {

    void writeRefreshCookie(HttpServletResponse response, String refreshPlain, Instant expireAt);

    void clearRefreshCookie(HttpServletResponse response);

    String refreshCookieName();
}
