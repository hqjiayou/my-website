package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.properties.AgentAiProperties;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Deque;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 滑窗 key 过期后必须从 map 移除，避免长期不再访问的 subject 只进不出。 */
class InMemoryAgentAiRateLimitServiceImplTest {

    @Test
    void laterAcquireDropsExpiredWindowsAndKeepsFreshOnes() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-30T00:00:00Z"));
        AgentAiProperties properties = new AgentAiProperties();
        properties.setRateLimitEnabled(true);
        properties.setRateLimitPerMinute(1);
        InMemoryAgentAiRateLimitServiceImpl service =
                new InMemoryAgentAiRateLimitServiceImpl(properties, clock);

        assertThat(service.tryAcquire(1L, "stale")).isTrue();
        assertThat(service.tryAcquire(2L, "fresh")).isTrue();

        clock.plusMillis(60_001L);

        assertThat(service.tryAcquire(3L, "next")).isTrue();
        assertThat(hasWindow(service, 1L, "stale")).isFalse();
        assertThat(hasWindow(service, 2L, "fresh")).isFalse();
        assertThat(hasWindow(service, 3L, "next")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasWindow(InMemoryAgentAiRateLimitServiceImpl service, long agentId, String subject)
            throws Exception {
        Field field = InMemoryAgentAiRateLimitServiceImpl.class.getDeclaredField("windows");
        field.setAccessible(true);
        Map<String, Deque<Long>> windows = (Map<String, Deque<Long>>) field.get(service);
        return windows.containsKey(agentId + ":" + subject);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void plusMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
