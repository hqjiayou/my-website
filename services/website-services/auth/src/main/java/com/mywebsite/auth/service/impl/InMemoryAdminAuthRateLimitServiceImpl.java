package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AdminAuthRedisKeyConstants;
import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版认证限流（语义对齐 {@link RedisAdminAuthRateLimitServiceImpl}）；用于 {@code test}
 * profile 或离线测试，不依赖 Redis。
 */
public class InMemoryAdminAuthRateLimitServiceImpl implements AdminAuthRateLimitService {

    private record Counter(long count, Instant expireAt) {}

    private final Object mutex = new Object();

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final AdminAuthProperties properties;

    private final Clock clock;

    public InMemoryAdminAuthRateLimitServiceImpl(AdminAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemoryAdminAuthRateLimitServiceImpl(AdminAuthProperties properties, Clock clock) {
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
    public boolean loginAllowed(String clientIp, String usernameNorm) {
        synchronized (mutex) {
            // 三维度全部检查（不短路），保证每个已超限维度都续期 TTL。
            boolean ipUserOk = checkAndRenewIfOverLimit(
                    AdminAuthRedisKeyConstants.rateLimitLoginIpUserKey(clientIp, usernameNorm),
                    properties.getAdminLoginIpUserFailureThreshold(),
                    properties.getAdminLoginIpUserWindowSeconds());
            boolean userOk = checkAndRenewIfOverLimit(
                    AdminAuthRedisKeyConstants.rateLimitLoginUserKey(usernameNorm),
                    properties.getAdminLoginUserFailureThreshold(),
                    properties.getAdminLoginUserWindowSeconds());
            boolean ipOk = checkAndRenewIfOverLimit(
                    AdminAuthRedisKeyConstants.rateLimitLoginIpKey(clientIp),
                    properties.getAdminLoginIpFailureThreshold(),
                    properties.getAdminLoginIpWindowSeconds());
            return ipUserOk && userOk && ipOk;
        }
    }

    @Override
    public void recordLoginFailure(String clientIp, String usernameNorm) {
        synchronized (mutex) {
            incrementWithWindow(
                    AdminAuthRedisKeyConstants.rateLimitLoginIpUserKey(clientIp, usernameNorm),
                    properties.getAdminLoginIpUserWindowSeconds());
            incrementWithWindow(
                    AdminAuthRedisKeyConstants.rateLimitLoginUserKey(usernameNorm),
                    properties.getAdminLoginUserWindowSeconds());
            incrementWithWindow(
                    AdminAuthRedisKeyConstants.rateLimitLoginIpKey(clientIp),
                    properties.getAdminLoginIpWindowSeconds());
        }
    }

    @Override
    public void resetLoginFailures(String clientIp, String usernameNorm) {
        synchronized (mutex) {
            // 成功登录只清 ipuser 与 user 两个维度；IP 维度保留。
            counters.remove(AdminAuthRedisKeyConstants.rateLimitLoginIpUserKey(clientIp, usernameNorm));
            counters.remove(AdminAuthRedisKeyConstants.rateLimitLoginUserKey(usernameNorm));
        }
    }

    @Override
    public boolean registerAllowed(String clientIp) {
        synchronized (mutex) {
            String key = AdminAuthRedisKeyConstants.rateLimitRegisterIpKey(clientIp);
            int windowSeconds = properties.getAdminRegisterIpWindowSeconds();
            long count = incrementWithWindow(key, windowSeconds);
            if (count > properties.getAdminRegisterIpThreshold()) {
                renewWindow(key, windowSeconds);
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        synchronized (mutex) {
            return checkAndRenewIfOverLimit(
                    AdminAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getAdminRefreshIpFailureThreshold(),
                    properties.getAdminRefreshIpWindowSeconds());
        }
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        synchronized (mutex) {
            incrementWithWindow(
                    AdminAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getAdminRefreshIpWindowSeconds());
        }
    }

    private long incrementWithWindow(String key, int windowSeconds) {
        Instant now = clock.instant();
        Counter current = counters.get(key);
        if (current == null || !now.isBefore(current.expireAt())) {
            counters.put(key, new Counter(1L, now.plusSeconds(windowSeconds)));
            return 1L;
        }
        Counter next = new Counter(current.count() + 1, current.expireAt());
        counters.put(key, next);
        return next.count();
    }

    /** 达阈值即拒绝，并把过期时间重置回完整窗口（对齐 Redis 版超限续期语义）。 */
    private boolean checkAndRenewIfOverLimit(String key, int threshold, int windowSeconds) {
        Instant now = clock.instant();
        Counter current = counters.get(key);
        if (current == null || !now.isBefore(current.expireAt())) {
            counters.remove(key);
            return true;
        }
        if (current.count() >= threshold) {
            renewWindow(key, windowSeconds);
            return false;
        }
        return true;
    }

    private void renewWindow(String key, int windowSeconds) {
        Counter current = counters.get(key);
        if (current != null) {
            counters.put(key, new Counter(current.count(), clock.instant().plusSeconds(windowSeconds)));
        }
    }
}
