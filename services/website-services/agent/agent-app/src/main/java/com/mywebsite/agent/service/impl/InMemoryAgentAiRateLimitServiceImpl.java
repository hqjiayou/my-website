package com.mywebsite.agent.service.impl;

import com.mywebsite.agent.properties.AgentAiProperties;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 最小进程内滑窗限流钩子（按 agentId + subject）。生产可替换为 Redis 实现而不改 Controller。
 */
@Service
public class InMemoryAgentAiRateLimitServiceImpl implements AgentAiRateLimitService {

    private final AgentAiProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryAgentAiRateLimitServiceImpl(AgentAiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryAgentAiRateLimitServiceImpl(AgentAiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(long agentId, String subject) {
        long now = clock.millis();
        // 只在访问时淘汰：整段滑出窗口的 key 从 map 移除，避免长期不访问的 subject 只进不出。
        evictExpired(now - 60_000L);
        if (!properties.isRateLimitEnabled()) {
            return true;
        }
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        long windowStart = now - 60_000L;
        String key = agentId + ":" + (subject == null ? "anonymous" : subject);
        while (true) {
            Deque<Long> q = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            synchronized (q) {
                // 淘汰与记次抢同一把锁；deque 已被换掉时重取，避免把时间戳写进已移出 map 的队列。
                if (windows.get(key) != q) {
                    continue;
                }
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

    private void evictExpired(long windowStart) {
        for (Map.Entry<String, Deque<Long>> entry : windows.entrySet()) {
            Deque<Long> q = entry.getValue();
            synchronized (q) {
                while (!q.isEmpty() && q.peekFirst() < windowStart) {
                    q.removeFirst();
                }
                if (q.isEmpty()) {
                    windows.remove(entry.getKey(), q);
                }
            }
        }
    }
}
