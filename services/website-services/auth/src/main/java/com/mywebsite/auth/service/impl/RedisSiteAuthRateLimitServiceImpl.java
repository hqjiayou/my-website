package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.SiteAuthRedisKeyConstants;
import com.mywebsite.auth.properties.SiteAuthProperties;
import com.mywebsite.auth.service.SiteAuthRateLimitService;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 版 Site 认证限流（由 {@link com.mywebsite.auth.config.RedisSiteAuthRateLimitConfig} 注册为 Bean）。
 *
 * <p>Redis 不可用时 fail-closed：不捕获、不吞异常，直接向上抛出。
 */
public class RedisSiteAuthRateLimitServiceImpl implements SiteAuthRateLimitService {

    private final StringRedisTemplate redis;

    private final SiteAuthProperties properties;

    public RedisSiteAuthRateLimitServiceImpl(StringRedisTemplate redis, SiteAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean loginAllowed(String clientIp, String siteCodeNorm, String usernameNorm) {
        boolean ipSiteUserOk = checkAndRenewIfOverLimit(
                SiteAuthRedisKeyConstants.rateLimitLoginIpSiteUserKey(clientIp, siteCodeNorm, usernameNorm),
                properties.getSiteLoginIpSiteUserFailureThreshold(),
                properties.getSiteLoginIpSiteUserWindowSeconds());
        boolean siteUserOk = checkAndRenewIfOverLimit(
                SiteAuthRedisKeyConstants.rateLimitLoginSiteUserKey(siteCodeNorm, usernameNorm),
                properties.getSiteLoginSiteUserFailureThreshold(),
                properties.getSiteLoginSiteUserWindowSeconds());
        boolean siteIpOk = checkAndRenewIfOverLimit(
                SiteAuthRedisKeyConstants.rateLimitLoginSiteIpKey(siteCodeNorm, clientIp),
                properties.getSiteLoginSiteIpFailureThreshold(),
                properties.getSiteLoginSiteIpWindowSeconds());
        return ipSiteUserOk && siteUserOk && siteIpOk;
    }

    @Override
    public void recordLoginFailure(String clientIp, String siteCodeNorm, String usernameNorm) {
        incrementWithWindow(
                SiteAuthRedisKeyConstants.rateLimitLoginIpSiteUserKey(clientIp, siteCodeNorm, usernameNorm),
                properties.getSiteLoginIpSiteUserWindowSeconds());
        incrementWithWindow(
                SiteAuthRedisKeyConstants.rateLimitLoginSiteUserKey(siteCodeNorm, usernameNorm),
                properties.getSiteLoginSiteUserWindowSeconds());
        incrementWithWindow(
                SiteAuthRedisKeyConstants.rateLimitLoginSiteIpKey(siteCodeNorm, clientIp),
                properties.getSiteLoginSiteIpWindowSeconds());
    }

    @Override
    public void resetLoginFailures(String clientIp, String siteCodeNorm, String usernameNorm) {
        redis.delete(SiteAuthRedisKeyConstants.rateLimitLoginIpSiteUserKey(clientIp, siteCodeNorm, usernameNorm));
        redis.delete(SiteAuthRedisKeyConstants.rateLimitLoginSiteUserKey(siteCodeNorm, usernameNorm));
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        return checkAndRenewIfOverLimit(
                SiteAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getSiteRefreshIpFailureThreshold(),
                properties.getSiteRefreshIpWindowSeconds());
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        incrementWithWindow(
                SiteAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getSiteRefreshIpWindowSeconds());
    }

    private long incrementWithWindow(String key, int windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        long value = count == null ? 1L : count;
        if (value == 1L) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return value;
    }

    private boolean checkAndRenewIfOverLimit(String key, int threshold, int windowSeconds) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return true;
        }
        long count = Long.parseLong(raw);
        if (count >= threshold) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
            return false;
        }
        return true;
    }
}
