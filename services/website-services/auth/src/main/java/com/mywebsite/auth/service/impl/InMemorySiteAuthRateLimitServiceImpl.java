package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.SiteAuthRedisKeyConstants;
import com.mywebsite.auth.properties.SiteAuthProperties;
import com.mywebsite.auth.service.SiteAuthRateLimitService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 Site 认证限流（语义对齐 {@link RedisSiteAuthRateLimitServiceImpl}）；用于 {@code test}
 * profile 或离线测试，不依赖 Redis。
 */
public class InMemorySiteAuthRateLimitServiceImpl implements SiteAuthRateLimitService {

    private record Counter(long count, Instant expireAt) {}

    private final Object mutex = new Object();

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final SiteAuthProperties properties;

    private final Clock clock;

    public InMemorySiteAuthRateLimitServiceImpl(SiteAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemorySiteAuthRateLimitServiceImpl(SiteAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 测试辅助：清空全部计数（内存实现专用）。 */
    public void clear() {
        synchronized (mutex) {
            counters.clear();
        }
    }

    @Override
    public boolean loginAllowed(String clientIp, String siteCodeNorm, String usernameNorm) {
        synchronized (mutex) {
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
    }

    @Override
    public void recordLoginFailure(String clientIp, String siteCodeNorm, String usernameNorm) {
        synchronized (mutex) {
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
    }

    @Override
    public void resetLoginFailures(String clientIp, String siteCodeNorm, String usernameNorm) {
        synchronized (mutex) {
            counters.remove(SiteAuthRedisKeyConstants.rateLimitLoginIpSiteUserKey(clientIp, siteCodeNorm, usernameNorm));
            counters.remove(SiteAuthRedisKeyConstants.rateLimitLoginSiteUserKey(siteCodeNorm, usernameNorm));
        }
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        synchronized (mutex) {
            return checkAndRenewIfOverLimit(
                    SiteAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getSiteRefreshIpFailureThreshold(),
                    properties.getSiteRefreshIpWindowSeconds());
        }
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        synchronized (mutex) {
            incrementWithWindow(
                    SiteAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getSiteRefreshIpWindowSeconds());
        }
    }

    private long incrementWithWindow(String key, int windowSeconds) {
        Instant now = clock.instant();
        Counter existing = counters.get(key);
        if (existing == null || !existing.expireAt().isAfter(now)) {
            counters.put(key, new Counter(1L, now.plusSeconds(windowSeconds)));
            return 1L;
        }
        long next = existing.count() + 1L;
        counters.put(key, new Counter(next, existing.expireAt()));
        return next;
    }

    private boolean checkAndRenewIfOverLimit(String key, int threshold, int windowSeconds) {
        Instant now = clock.instant();
        Counter existing = counters.get(key);
        if (existing == null || !existing.expireAt().isAfter(now)) {
            counters.remove(key);
            return true;
        }
        if (existing.count() >= threshold) {
            renewWindow(key, windowSeconds);
            return false;
        }
        return true;
    }

    private void renewWindow(String key, int windowSeconds) {
        Counter existing = counters.get(key);
        if (existing == null) {
            return;
        }
        counters.put(key, new Counter(existing.count(), clock.instant().plusSeconds(windowSeconds)));
    }
}
