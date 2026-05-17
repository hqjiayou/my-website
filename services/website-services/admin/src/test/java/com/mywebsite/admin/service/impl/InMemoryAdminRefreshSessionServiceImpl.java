package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.dto.RotateRefreshDto;
import com.mywebsite.admin.service.AdminRefreshSessionService;
import com.mywebsite.admin.utils.RefreshTokenHashUtils;
import com.mywebsite.auth.token.RefreshTokenGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 离线测试用 Refresh 会话存储（语义对齐 {@link RedisAdminRefreshSessionServiceImpl}）；仅放于 test classpath，
 * 由 {@link com.mywebsite.admin.config.AdminRefreshSessionTestBeans} 在缺少 {@code RedisConnectionFactory} 时注册。
 */
public class InMemoryAdminRefreshSessionServiceImpl implements AdminRefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAdminRefreshSessionServiceImpl.class);

    private final Object mutex = new Object();

    private final Map<String, Session> sessionsById = new ConcurrentHashMap<>();

    private final Map<String, String> refreshToSession = new ConcurrentHashMap<>();

    private final Map<String, String> replayToSession = new ConcurrentHashMap<>();

    @Override
    public void bindNewSession(long adminUserId, String username, String sessionId, String refreshPlain, Instant expireAt) {
        synchronized (mutex) {
            String h = RefreshTokenHashUtils.sha256Hex(refreshPlain);
            Session s = new Session(sessionId, adminUserId, username, h, Instant.now(), expireAt);
            sessionsById.put(sessionId, s);
            refreshToSession.put(h, sessionId);
        }
    }

    @Override
    public RotateRefreshDto rotate(String incomingRefreshPlain) {
        String hIn = RefreshTokenHashUtils.sha256Hex(incomingRefreshPlain);
        synchronized (mutex) {
            String sid = refreshToSession.get(hIn);
            if (sid == null) {
                String replaySid = replayToSession.get(hIn);
                if (replaySid != null) {
                    log.warn("Admin refresh replay detected (in-memory), revoking session {}", replaySid);
                    purgeSession(replaySid);
                    replayToSession.remove(hIn);
                    return new RotateRefreshDto.ReplayRevoked();
                }
                return new RotateRefreshDto.Invalid();
            }
            Session session = sessionsById.get(sid);
            if (session == null || !Objects.equals(session.refreshHash(), hIn)) {
                return new RotateRefreshDto.Invalid();
            }
            if (Instant.now().isAfter(session.expireAt())) {
                purgeSession(sid);
                return new RotateRefreshDto.Invalid();
            }
            refreshToSession.remove(hIn);
            replayToSession.put(hIn, sid);

            String newPlain = RefreshTokenGenerator.generateOpaque();
            String hNew = RefreshTokenHashUtils.sha256Hex(newPlain);
            Session rotated =
                    new Session(sid, session.adminUserId(), session.username(), hNew, session.issuedAt(), session.expireAt());
            sessionsById.put(sid, rotated);
            refreshToSession.put(hNew, sid);

            return new RotateRefreshDto.Rotated(
                    session.adminUserId(), session.username(), sid, newPlain, session.expireAt());
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
            long adminUserId,
            String username,
            String refreshHash,
            Instant issuedAt,
            Instant expireAt
    ) {}
}
