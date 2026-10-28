package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AgentAuthRedisKeyConstants;
import com.mywebsite.auth.properties.AgentAuthProperties;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 Agent 认证限流（语义对齐 {@link RedisAgentAuthRateLimitServiceImpl}）；用于 {@code test}
 * profile 或离线测试，不依赖 Redis。
 */
public class InMemoryAgentAuthRateLimitServiceImpl implements AgentAuthRateLimitService {

    private record Counter(long count, Instant expireAt) {}

    private final Object mutex = new Object();

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final AgentAuthProperties properties;

    private final Clock clock;

    public InMemoryAgentAuthRateLimitServiceImpl(AgentAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemoryAgentAuthRateLimitServiceImpl(AgentAuthProperties properties, Clock clock) {
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
    public boolean loginAllowed(String clientIp, String agentCodeNorm, String usernameNorm) {
        synchronized (mutex) {
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
    }

    @Override
    public void recordLoginFailure(String clientIp, String agentCodeNorm, String usernameNorm) {
        synchronized (mutex) {
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
    }

    @Override
    public void resetLoginFailures(String clientIp, String agentCodeNorm, String usernameNorm) {
        synchronized (mutex) {
            counters.remove(AgentAuthRedisKeyConstants.rateLimitLoginIpAgentUserKey(clientIp, agentCodeNorm, usernameNorm));
            counters.remove(AgentAuthRedisKeyConstants.rateLimitLoginAgentUserKey(agentCodeNorm, usernameNorm));
        }
    }

    @Override
    public boolean refreshAllowed(String clientIp) {
        synchronized (mutex) {
            return checkAndRenewIfOverLimit(
                    AgentAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getAgentRefreshIpFailureThreshold(),
                    properties.getAgentRefreshIpWindowSeconds());
        }
    }

    @Override
    public void recordRefreshFailure(String clientIp) {
        synchronized (mutex) {
            incrementWithWindow(
                    AgentAuthRedisKeyConstants.rateLimitRefreshIpKey(clientIp),
                    properties.getAgentRefreshIpWindowSeconds());
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
