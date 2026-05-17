package com.mywebsite.admin.service;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;

/** 根据配置的 Cookie 属性写入或清除 Refresh HttpOnly Cookie。 */
public interface AdminRefreshCookieService {

    void writeRefreshCookie(HttpServletResponse response, String refreshPlain, Instant expireAt);

    void clearRefreshCookie(HttpServletResponse response);

    String refreshCookieName();
}
