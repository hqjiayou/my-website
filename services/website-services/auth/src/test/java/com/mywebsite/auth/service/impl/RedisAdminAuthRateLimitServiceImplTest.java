package com.mywebsite.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mywebsite.auth.properties.AdminAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 不可用时限流实现 fail-closed：不吞异常，向上抛出。
 */
class RedisAdminAuthRateLimitServiceImplTest {

    private StringRedisTemplate redis;

    private ValueOperations<String, String> valueOps;

    private RedisAdminAuthRateLimitServiceImpl rateLimit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setAdminLoginIpUserFailureThreshold(3);
        properties.setAdminLoginUserFailureThreshold(5);
        properties.setAdminLoginIpFailureThreshold(8);
        properties.setAdminRegisterIpThreshold(3);
        properties.setAdminRefreshIpFailureThreshold(3);
        rateLimit = new RedisAdminAuthRateLimitServiceImpl(redis, properties);
    }

    @Test
    void loginAllowedPropagatesRedisConnectionFailure() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> rateLimit.loginAllowed("10.0.0.1", "alice"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void registerAllowedPropagatesRedisConnectionFailure() {
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> rateLimit.registerAllowed("10.0.0.2"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void refreshAllowedPropagatesRedisConnectionFailure() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> rateLimit.refreshAllowed("10.0.0.3"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
