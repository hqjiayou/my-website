package com.mywebsite.site.service.impl;

import com.mywebsite.site.properties.SiteAiProperties;
import com.mywebsite.site.service.SiteAiRateLimitService;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 最小进程内滑窗限流钩子（按 siteId + subject）。生产可替换为 Redis 实现而不改 Controller。
 */
@Service
public class InMemorySiteAiRateLimitServiceImpl implements SiteAiRateLimitService {

    private final SiteAiProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public InMemorySiteAiRateLimitServiceImpl(SiteAiProperties properties) {
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Override
    public boolean tryAcquire(long siteId, String subject) {
        if (!properties.isRateLimitEnabled()) {
            return true;
        }
        int limit = Math.max(1, properties.getRateLimitPerMinute());
        long now = clock.millis();
        long windowStart = now - 60_000L;
        String key = siteId + ":" + (subject == null ? "anonymous" : subject);
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
