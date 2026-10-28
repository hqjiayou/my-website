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

    /** Agent 前台 Refresh Cookie（与 Admin 完全隔离）。 */
    private String agentRefreshCookieName = "MYWEBSITE_SITE_REFRESH_TOKEN";

    private String agentRefreshCookiePath = "/api/auth/agent";

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

    public String getAgentRefreshCookieName() {
        return agentRefreshCookieName;
    }

    public void setAgentRefreshCookieName(String agentRefreshCookieName) {
        this.agentRefreshCookieName = agentRefreshCookieName;
    }

    public String getAgentRefreshCookiePath() {
        return agentRefreshCookiePath;
    }

    public void setAgentRefreshCookiePath(String agentRefreshCookiePath) {
        this.agentRefreshCookiePath = agentRefreshCookiePath;
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
