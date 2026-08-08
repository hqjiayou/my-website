package com.mywebsite.auth.service;

/** Site 登录 / 刷新限流（维度含 siteCode）。 */
public interface SiteAuthRateLimitService {

    boolean loginAllowed(String clientIp, String siteCodeNorm, String usernameNorm);

    void recordLoginFailure(String clientIp, String siteCodeNorm, String usernameNorm);

    void resetLoginFailures(String clientIp, String siteCodeNorm, String usernameNorm);

    boolean refreshAllowed(String clientIp);

    void recordRefreshFailure(String clientIp);
}
