package com.mywebsite.common.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 跨模块共享的访问令牌 / Refresh Cookie 外部化配置（前缀 {@code mywebsite.security.token}）。 */
@ConfigurationProperties(prefix = "mywebsite.security.token")
public class TokenProperties {

    private long accessTokenExpireSeconds = 900;

    private long refreshTokenExpireSeconds = 604800;

    private String refreshCookieName = "MYWEBSITE_REFRESH_TOKEN";

    private String refreshCookiePath = "/api/auth/admin";

    /** Site 前台 Refresh Cookie（与 Admin 完全隔离）。 */
    private String siteRefreshCookieName = "MYWEBSITE_SITE_REFRESH_TOKEN";

    private String siteRefreshCookiePath = "/api/auth/site";

    private boolean refreshCookieSecure = true;

    private boolean refreshCookieHttpOnly = true;

    /** Strict | Lax | None */
    private String refreshCookieSameSite = "Lax";

    public Duration accessTokenTtl() {
        return Duration.ofSeconds(accessTokenExpireSeconds);
    }

    public Duration refreshTokenTtl() {
        return Duration.ofSeconds(refreshTokenExpireSeconds);
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
    }

    public long getRefreshTokenExpireSeconds() {
        return refreshTokenExpireSeconds;
    }

    public void setRefreshTokenExpireSeconds(long refreshTokenExpireSeconds) {
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public String getRefreshCookiePath() {
        return refreshCookiePath;
    }

    public void setRefreshCookiePath(String refreshCookiePath) {
        this.refreshCookiePath = refreshCookiePath;
    }

    public String getSiteRefreshCookieName() {
        return siteRefreshCookieName;
    }

    public void setSiteRefreshCookieName(String siteRefreshCookieName) {
        this.siteRefreshCookieName = siteRefreshCookieName;
    }

    public String getSiteRefreshCookiePath() {
        return siteRefreshCookiePath;
    }

    public void setSiteRefreshCookiePath(String siteRefreshCookiePath) {
        this.siteRefreshCookiePath = siteRefreshCookiePath;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public boolean isRefreshCookieHttpOnly() {
        return refreshCookieHttpOnly;
    }

    public void setRefreshCookieHttpOnly(boolean refreshCookieHttpOnly) {
        this.refreshCookieHttpOnly = refreshCookieHttpOnly;
    }

    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }
}
