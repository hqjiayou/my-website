package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AdminAuthRedisKeyConstants;
import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 版认证限流（由 {@link com.mywebsite.auth.config.RedisAdminAuthRateLimitConfig} 注册为 Bean）。
 *
 * <p>Redis 不可用时 fail-closed：不捕获、不吞异常，直接向上抛出——与
 * {@link RedisAdminRefreshSessionServiceImpl} 行为一致，认证入口宁可不可用也不放开暴力尝试。
 */
public class RedisAdminAuthRateLimitServiceImpl implements AdminAuthRateLimitService {

    private final StringRedisTemplate redis;

    private final AdminAuthProperties properties;

    public RedisAdminAuthRateLimitServiceImpl(StringRedisTemplate redis, AdminAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean loginAllowed(String clientIp, String usernameNorm) {
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

    @Override
    public void recordLoginFailure(String clientIp, String usernameNorm) {
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

    @Override
    public void resetLoginFailures(String clientIp, String usernameNorm) {
        // 成功登录只清 ipuser 与 user 两个维度；IP 维度保留，跨 username 撞库不因偶发成功而清零。
        redis.delete(AdminAuthRedisKeyConstants.rateLimitLoginIpUserKey(clientIp, usernameNorm));
        redis.delete(AdminAuthRedisKeyConstants.rateLimitLoginUserKey(usernameNorm));
    }

    @Override
    public boolean registerAllowed(String clientIp) {
        String key = AdminAuthRedisKeyConstants.rateLimitRegisterIpKey(clientIp);
        int windowSeconds = properties.getAdminRegisterIpWindowSeconds();
        long count = incrementWithWindow(key, windowSeconds);
        if (count > properties.getAdminRegisterIpThreshold()) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
            return false;
        }
        return true;
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        return checkAndRenewIfOverLimit(
                AdminAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getAdminRefreshIpFailureThreshold(),
                properties.getAdminRefreshIpWindowSeconds());
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        incrementWithWindow(
                AdminAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getAdminRefreshIpWindowSeconds());
    }

    private long incrementWithWindow(String key, int windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        long value = count == null ? 1L : count;
        if (value == 1L) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return value;
    }

    /** 达阈值即拒绝，并把 TTL 重置回完整窗口（超限期间持续尝试 → cooldown 持续顺延）。 */
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
