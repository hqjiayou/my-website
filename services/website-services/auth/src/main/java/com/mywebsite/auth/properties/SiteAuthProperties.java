package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Site 登录策略配置（前缀 {@code security.site-auth}）。 */
@ConfigurationProperties(prefix = "security.site-auth")
public class SiteAuthProperties {

    private int siteLoginMaxPasswordLength = 128;

    /** 认证限流总开关（逃生阀）；关闭后登录 / 刷新均不做限流检查与计数。 */
    private boolean siteRateLimitEnabled = true;

    private int siteLoginIpSiteUserFailureThreshold = 5;

    private int siteLoginIpSiteUserWindowSeconds = 300;

    private int siteLoginSiteUserFailureThreshold = 10;

    private int siteLoginSiteUserWindowSeconds = 900;

    private int siteLoginSiteIpFailureThreshold = 20;

    private int siteLoginSiteIpWindowSeconds = 900;

    private int siteRefreshIpFailureThreshold = 30;

    private int siteRefreshIpWindowSeconds = 600;

    public int getSiteLoginMaxPasswordLength() {
        return siteLoginMaxPasswordLength;
    }

    public void setSiteLoginMaxPasswordLength(int siteLoginMaxPasswordLength) {
        this.siteLoginMaxPasswordLength = siteLoginMaxPasswordLength;
    }

    public boolean isSiteRateLimitEnabled() {
        return siteRateLimitEnabled;
    }

    public void setSiteRateLimitEnabled(boolean siteRateLimitEnabled) {
        this.siteRateLimitEnabled = siteRateLimitEnabled;
    }

    public int getSiteLoginIpSiteUserFailureThreshold() {
        return siteLoginIpSiteUserFailureThreshold;
    }

    public void setSiteLoginIpSiteUserFailureThreshold(int siteLoginIpSiteUserFailureThreshold) {
        this.siteLoginIpSiteUserFailureThreshold = siteLoginIpSiteUserFailureThreshold;
    }

    public int getSiteLoginIpSiteUserWindowSeconds() {
        return siteLoginIpSiteUserWindowSeconds;
    }

    public void setSiteLoginIpSiteUserWindowSeconds(int siteLoginIpSiteUserWindowSeconds) {
        this.siteLoginIpSiteUserWindowSeconds = siteLoginIpSiteUserWindowSeconds;
    }

    public int getSiteLoginSiteUserFailureThreshold() {
        return siteLoginSiteUserFailureThreshold;
    }

    public void setSiteLoginSiteUserFailureThreshold(int siteLoginSiteUserFailureThreshold) {
        this.siteLoginSiteUserFailureThreshold = siteLoginSiteUserFailureThreshold;
    }

    public int getSiteLoginSiteUserWindowSeconds() {
        return siteLoginSiteUserWindowSeconds;
    }

    public void setSiteLoginSiteUserWindowSeconds(int siteLoginSiteUserWindowSeconds) {
        this.siteLoginSiteUserWindowSeconds = siteLoginSiteUserWindowSeconds;
    }

    public int getSiteLoginSiteIpFailureThreshold() {
        return siteLoginSiteIpFailureThreshold;
    }

    public void setSiteLoginSiteIpFailureThreshold(int siteLoginSiteIpFailureThreshold) {
        this.siteLoginSiteIpFailureThreshold = siteLoginSiteIpFailureThreshold;
    }

    public int getSiteLoginSiteIpWindowSeconds() {
        return siteLoginSiteIpWindowSeconds;
    }

    public void setSiteLoginSiteIpWindowSeconds(int siteLoginSiteIpWindowSeconds) {
        this.siteLoginSiteIpWindowSeconds = siteLoginSiteIpWindowSeconds;
    }

    public int getSiteRefreshIpFailureThreshold() {
        return siteRefreshIpFailureThreshold;
    }

    public void setSiteRefreshIpFailureThreshold(int siteRefreshIpFailureThreshold) {
        this.siteRefreshIpFailureThreshold = siteRefreshIpFailureThreshold;
    }

    public int getSiteRefreshIpWindowSeconds() {
        return siteRefreshIpWindowSeconds;
    }

    public void setSiteRefreshIpWindowSeconds(int siteRefreshIpWindowSeconds) {
        this.siteRefreshIpWindowSeconds = siteRefreshIpWindowSeconds;
    }
}
