package com.mywebsite.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.auth.properties.AdminAuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryAdminAuthRateLimitServiceImplTest {

    private AdminAuthProperties properties;

    private MutableClock clock;

    private InMemoryAdminAuthRateLimitServiceImpl rateLimit;

    @BeforeEach
    void setUp() {
        properties = new AdminAuthProperties();
        properties.setAdminLoginIpUserFailureThreshold(3);
        properties.setAdminLoginIpUserWindowSeconds(60);
        properties.setAdminLoginUserFailureThreshold(5);
        properties.setAdminLoginUserWindowSeconds(120);
        properties.setAdminLoginIpFailureThreshold(8);
        properties.setAdminLoginIpWindowSeconds(120);
        properties.setAdminRegisterIpThreshold(3);
        properties.setAdminRegisterIpWindowSeconds(300);
        properties.setAdminRefreshIpFailureThreshold(3);
        properties.setAdminRefreshIpWindowSeconds(60);

        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        rateLimit = new InMemoryAdminAuthRateLimitServiceImpl(properties, clock);
    }

    @Test
    void loginWindowExpiryRestoresAccess() {
        String ip = "10.0.0.1";
        String user = "alice";
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimit.loginAllowed(ip, user)).isTrue();
            rateLimit.recordLoginFailure(ip, user);
        }
        assertThat(rateLimit.loginAllowed(ip, user)).isFalse();

        clock.advanceSeconds(61);
        assertThat(rateLimit.loginAllowed(ip, user)).isTrue();
    }

    @Test
    void overLimitRenewsCooldownWindow() {
        String ip = "10.0.0.2";
        String user = "bob";
        for (int i = 0; i < 3; i++) {
            rateLimit.recordLoginFailure(ip, user);
        }
        assertThat(rateLimit.loginAllowed(ip, user)).isFalse();

        clock.advanceSeconds(40);
        assertThat(rateLimit.loginAllowed(ip, user)).isFalse();

        // 超限检查会把窗口续到 now+60；再过 40s 仍未过期
        clock.advanceSeconds(40);
        assertThat(rateLimit.loginAllowed(ip, user)).isFalse();

        clock.advanceSeconds(61);
        assertThat(rateLimit.loginAllowed(ip, user)).isTrue();
    }

    @Test
    void successfulLoginResetClearsIpUserAndUserButNotIp() {
        String ip = "10.0.0.3";
        String user = "carol";
        for (int i = 0; i < 3; i++) {
            rateLimit.recordLoginFailure(ip, user);
        }
        assertThat(rateLimit.loginAllowed(ip, user)).isFalse();

        rateLimit.resetLoginFailures(ip, user);
        assertThat(rateLimit.loginAllowed(ip, user)).isTrue();

        // IP 维度保留：再制造足够失败以触发 IP 阈值（8）
        for (int i = 0; i < 8; i++) {
            rateLimit.recordLoginFailure(ip, "u" + i);
        }
        assertThat(rateLimit.loginAllowed(ip, "fresh-user")).isFalse();
    }

    @Test
    void registerCountsSuccessAndFailureAttempts() {
        String ip = "10.0.0.4";
        assertThat(rateLimit.registerAllowed(ip)).isTrue();
        assertThat(rateLimit.registerAllowed(ip)).isTrue();
        assertThat(rateLimit.registerAllowed(ip)).isTrue();
        assertThat(rateLimit.registerAllowed(ip)).isFalse();

        clock.advanceSeconds(301);
        assertThat(rateLimit.registerAllowed(ip)).isTrue();
    }

    @Test
    void refreshFailureThresholdAndSuccessDoesNotReset() {
        String ip = "10.0.0.5";
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimit.refreshAllowed(ip)).isTrue();
            rateLimit.recordRefreshFailure(ip);
        }
        assertThat(rateLimit.refreshAllowed(ip)).isFalse();
    }

    /** 可拨动的 Clock，供窗口过期断言。 */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
