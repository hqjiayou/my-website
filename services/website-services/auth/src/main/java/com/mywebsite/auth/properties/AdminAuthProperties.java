package com.mywebsite.auth.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin 认证与会话 Cookie 绑定（前缀 {@code security.auth}）。 */
@ConfigurationProperties(prefix = "security.auth")
public class AdminAuthProperties {

    private Duration refreshTokenTtl = Duration.ofDays(7);

    private String refreshCookieName = "admin_refresh_token";

    private String refreshCookiePath = "/api/admin/auth";

    private boolean refreshCookieSecure = true;

    /** Strict | Lax | None */
    private String refreshCookieSameSite = "Strict";

    private int adminLoginMaxPasswordLength = 128;

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
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

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    public int getAdminLoginMaxPasswordLength() {
        return adminLoginMaxPasswordLength;
    }

    public void setAdminLoginMaxPasswordLength(int adminLoginMaxPasswordLength) {
        this.adminLoginMaxPasswordLength = adminLoginMaxPasswordLength;
    }
}
