package com.mywebsite.agent.service.impl;

import com.mywebsite.agent.properties.AgentAiProperties;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 最小进程内滑窗限流钩子（按 agentId + subject）。生产可替换为 Redis 实现而不改 Controller。
 */
@Service
public class InMemoryAgentAiRateLimitServiceImpl implements AgentAiRateLimitService {

    private final AgentAiProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public InMemoryAgentAiRateLimitServiceImpl(AgentAiProperties properties) {
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Override
    public boolean tryAcquire(long agentId, String subject) {
        if (!properties.isRateLimitEnabled()) {
            return true;
        }
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        long now = clock.millis();
        long windowStart = now - 60_000L;
        String key = agentId + ":" + (subject == null ? "anonymous" : subject);
        Deque<Long> q = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.removeFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
