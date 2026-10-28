package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AgentAuthRedisKeyConstants;
import com.mywebsite.auth.properties.AgentAuthProperties;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 版 Agent 认证限流（由 {@link com.mywebsite.auth.config.RedisAgentAuthRateLimitConfig} 注册为 Bean）。
 *
 * <p>Redis 不可用时 fail-closed：不捕获、不吞异常，直接向上抛出。
 */
public class RedisAgentAuthRateLimitServiceImpl implements AgentAuthRateLimitService {

    private final StringRedisTemplate redis;

    private final AgentAuthProperties properties;

    public RedisAgentAuthRateLimitServiceImpl(StringRedisTemplate redis, AgentAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean loginAllowed(String clientIp, String agentCodeNorm, String usernameNorm) {
        boolean ipAgentUserOk = checkAndRenewIfOverLimit(
                AgentAuthRedisKeyConstants.rateLimitLoginIpAgentUserKey(clientIp, agentCodeNorm, usernameNorm),
                properties.getAgentLoginIpAgentUserFailureThreshold(),
                properties.getAgentLoginIpAgentUserWindowSeconds());
        boolean agentUserOk = checkAndRenewIfOverLimit(
                AgentAuthRedisKeyConstants.rateLimitLoginAgentUserKey(agentCodeNorm, usernameNorm),
                properties.getAgentLoginAgentUserFailureThreshold(),
                properties.getAgentLoginAgentUserWindowSeconds());
        boolean agentIpOk = checkAndRenewIfOverLimit(
                AgentAuthRedisKeyConstants.rateLimitLoginAgentIpKey(agentCodeNorm, clientIp),
                properties.getAgentLoginAgentIpFailureThreshold(),
                properties.getAgentLoginAgentIpWindowSeconds());
        return ipAgentUserOk && agentUserOk && agentIpOk;
    }

    @Override
    public void recordLoginFailure(String clientIp, String agentCodeNorm, String usernameNorm) {
        incrementWithWindow(
                AgentAuthRedisKeyConstants.rateLimitLoginIpAgentUserKey(clientIp, agentCodeNorm, usernameNorm),
                properties.getAgentLoginIpAgentUserWindowSeconds());
        incrementWithWindow(
                AgentAuthRedisKeyConstants.rateLimitLoginAgentUserKey(agentCodeNorm, usernameNorm),
                properties.getAgentLoginAgentUserWindowSeconds());
        incrementWithWindow(
                AgentAuthRedisKeyConstants.rateLimitLoginAgentIpKey(agentCodeNorm, clientIp),
                properties.getAgentLoginAgentIpWindowSeconds());
    }

    @Override
    public void resetLoginFailures(String clientIp, String agentCodeNorm, String usernameNorm) {
        redis.delete(AgentAuthRedisKeyConstants.rateLimitLoginIpAgentUserKey(clientIp, agentCodeNorm, usernameNorm));
        redis.delete(AgentAuthRedisKeyConstants.rateLimitLoginAgentUserKey(agentCodeNorm, usernameNorm));
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        return checkAndRenewIfOverLimit(
                AgentAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getAgentRefreshIpFailureThreshold(),
                properties.getAgentRefreshIpWindowSeconds());
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        incrementWithWindow(
                AgentAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                properties.getAgentRefreshIpWindowSeconds());
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
