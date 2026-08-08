package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.service.SiteRefreshCookieService;
import com.mywebsite.common.security.config.TokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SiteRefreshCookieServiceImpl implements SiteRefreshCookieService {

    private final TokenProperties tokenProperties;

    public SiteRefreshCookieServiceImpl(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
    }

    @Override
    public void writeRefreshCookie(HttpServletResponse response, String refreshPlain, Instant expireAt) {
        int maxAge = (int) Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        Cookie cookie = new Cookie(tokenProperties.getSiteRefreshCookieName(), refreshPlain);
        cookie.setHttpOnly(tokenProperties.isRefreshCookieHttpOnly());
        cookie.setPath(tokenProperties.getSiteRefreshCookiePath());
        cookie.setSecure(tokenProperties.isRefreshCookieSecure());
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", tokenProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(tokenProperties.getSiteRefreshCookieName(), "");
        cookie.setHttpOnly(tokenProperties.isRefreshCookieHttpOnly());
        cookie.setPath(tokenProperties.getSiteRefreshCookiePath());
        cookie.setSecure(tokenProperties.isRefreshCookieSecure());
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", tokenProperties.getRefreshCookieSameSite());
        response.addCookie(cookie);
    }

    @Override
    public String refreshCookieName() {
        return tokenProperties.getSiteRefreshCookieName();
    }
}
