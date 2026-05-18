package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.service.AdminRefreshCookieService;
import com.mywebsite.common.security.config.TokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AdminRefreshCookieServiceImpl implements AdminRefreshCookieService {

    private final TokenProperties tokenProperties;

    public AdminRefreshCookieServiceImpl(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Override
    public void writeRefreshCookie(HttpServletResponse response, String refreshPlain, Instant expireAt) {
        int maxAge = (int) Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        Cookie cookie = new Cookie(tokenProperties.getRefreshCookieName(), refreshPlain);
        cookie.setHttpOnly(tokenProperties.isRefreshCookieHttpOnly());
        cookie.setPath(tokenProperties.getRefreshCookiePath());
        cookie.setSecure(tokenProperties.isRefreshCookieSecure());
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", tokenProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(tokenProperties.getRefreshCookieName(), "");
        cookie.setHttpOnly(tokenProperties.isRefreshCookieHttpOnly());
        cookie.setPath(tokenProperties.getRefreshCookiePath());
        cookie.setSecure(tokenProperties.isRefreshCookieSecure());
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", tokenProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public String refreshCookieName() {
        return tokenProperties.getRefreshCookieName();
    }
}
