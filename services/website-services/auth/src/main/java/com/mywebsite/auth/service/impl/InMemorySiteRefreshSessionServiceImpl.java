package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.dto.SiteRotateRefreshDto;
import com.mywebsite.auth.service.SiteRefreshSessionService;
import com.mywebsite.auth.utils.RefreshTokenHashUtils;
import com.mywebsite.auth.utils.RefreshTokenUtils;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内存版 Site Refresh 会话（语义对齐 {@link RedisSiteRefreshSessionServiceImpl}）；用于 {@code test}
 * profile 或离线测试，不依赖 Redis。
 */
public class InMemorySiteRefreshSessionServiceImpl implements SiteRefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(InMemorySiteRefreshSessionServiceImpl.class);

    private final Object mutex = new Object();

    private final Map<String, Session> sessionsById = new ConcurrentHashMap<>();

    private final Map<String, String> refreshToSession = new ConcurrentHashMap<>();

    private final Map<String, String> replayToSession = new ConcurrentHashMap<>();

    @Override
    public void bindNewSession(
            long siteUserId, long siteId, String username, String sessionId, String refreshPlain, Instant expireAt) {
        synchronized (mutex) {
            String h = RefreshTokenHashUtils.sha256Hex(refreshPlain);
            Session s = new Session(sessionId, siteUserId, siteId, username, h, Instant.now(), expireAt);
            sessionsById.put(sessionId, s);
            refreshToSession.put(h, sessionId);
        }
    }

    @Override
    public SiteRotateRefreshDto rotate(String incomingRefreshPlain) {
        String hIn = RefreshTokenHashUtils.sha256Hex(incomingRefreshPlain);
        synchronized (mutex) {
            String sid = refreshToSession.get(hIn);
            if (sid == null) {
                String replaySid = replayToSession.get(hIn);
                if (replaySid != null) {
                    log.warn("Site refresh replay detected (in-memory), revoking session {}", replaySid);
                    purgeSession(replaySid);
                    replayToSession.remove(hIn);
                    return new SiteRotateRefreshDto.ReplayRevoked();
                }
                return new SiteRotateRefreshDto.Invalid();
            }
            Session session = sessionsById.get(sid);
            if (session == null || !Objects.equals(session.refreshHash(), hIn)) {
                return new SiteRotateRefreshDto.Invalid();
            }
            if (Instant.now().isAfter(session.expireAt())) {
                purgeSession(sid);
                return new SiteRotateRefreshDto.Invalid();
            }
            refreshToSession.remove(hIn);
            replayToSession.put(hIn, sid);

            String newPlain = RefreshTokenUtils.generateOpaque();
            String hNew = RefreshTokenHashUtils.sha256Hex(newPlain);
            Session rotated = new Session(
                    sid,
                    session.siteUserId(),
                    session.siteId(),
                    session.username(),
                    hNew,
                    session.issuedAt(),
                    session.expireAt());
            sessionsById.put(sid, rotated);
            refreshToSession.put(hNew, sid);

            return new SiteRotateRefreshDto.Rotated(
                    session.siteUserId(),
                    session.siteId(),
                    session.username(),
                    sid,
                    newPlain,
                    session.expireAt());
        }
    }

    @Override
    public void revokeByRefreshCookie(Optional<String> refreshPlain) {
        if (refreshPlain.isEmpty() || refreshPlain.get().isBlank()) {
            return;
        }
        String h = RefreshTokenHashUtils.sha256Hex(refreshPlain.get());
        synchronized (mutex) {
            String sid = refreshToSession.remove(h);
            if (sid != null) {
                purgeSession(sid);
                return;
            }
            String replaySid = replayToSession.remove(h);
            if (replaySid != null) {
                purgeSession(replaySid);
            }
        }
    }

    private void purgeSession(String sessionId) {
        Session s = sessionsById.remove(sessionId);
        if (s == null) {
            return;
        }
        refreshToSession.entrySet().removeIf(e -> sessionId.equals(e.getValue()));
        replayToSession.entrySet().removeIf(e -> sessionId.equals(e.getValue()));
    }

    private record Session(
            String sessionId,
            long siteUserId,
            long siteId,
            String username,
            String refreshHash,
            Instant issuedAt,
            Instant expireAt) {}
}
