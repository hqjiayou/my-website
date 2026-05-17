package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.service.AdminRefreshCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AdminRefreshCookieServiceImpl implements AdminRefreshCookieService {

    private final AdminAuthProperties adminAuthProperties;

    public AdminRefreshCookieServiceImpl(AdminAuthProperties adminAuthProperties) {
        this.adminAuthProperties = adminAuthProperties;
    }

    @Override
    public void writeRefreshCookie(HttpServletResponse response, String refreshPlain, Instant expireAt) {
        int maxAge = (int) Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        Cookie cookie = new Cookie(adminAuthProperties.getRefreshCookieName(), refreshPlain);
        cookie.setHttpOnly(true);
        cookie.setPath(adminAuthProperties.getRefreshCookiePath());
        cookie.setSecure(adminAuthProperties.isRefreshCookieSecure());
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", adminAuthProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(adminAuthProperties.getRefreshCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setPath(adminAuthProperties.getRefreshCookiePath());
        cookie.setSecure(adminAuthProperties.isRefreshCookieSecure());
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", adminAuthProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public String refreshCookieName() {
        return adminAuthProperties.getRefreshCookieName();
    }
}
