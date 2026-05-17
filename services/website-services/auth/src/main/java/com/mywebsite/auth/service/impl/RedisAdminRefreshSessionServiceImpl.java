package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AdminAuthRedisKeyConstants;
import com.mywebsite.auth.dto.RotateRefreshDto;
import com.mywebsite.auth.service.AdminRefreshSessionService;
import com.mywebsite.auth.utils.RefreshTokenUtils;
import com.mywebsite.auth.utils.RefreshTokenHashUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 由 {@link com.mywebsite.auth.config.RedisAdminRefreshSessionConfig} 注册为 Bean。 */
public class RedisAdminRefreshSessionServiceImpl implements AdminRefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RedisAdminRefreshSessionServiceImpl.class);

    private static final String F_ADMIN_USER_ID = "adminUserId";

    private static final String F_USERNAME = "username";

    private static final String F_REFRESH_HASH = "refreshTokenHash";

    private static final String F_ISSUED_AT = "issuedAtMillis";

    private static final String F_EXPIRE_AT = "expireAtMillis";

    private final StringRedisTemplate redis;

    public RedisAdminRefreshSessionServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void bindNewSession(long adminUserId, String username, String sessionId, String refreshPlain, Instant expireAt) {
        Instant now = Instant.now();
        String refreshHash = RefreshTokenHashUtils.sha256Hex(refreshPlain);
        String sessionKey = AdminAuthRedisKeyConstants.sessionKey(sessionId);
        long ttlSecs = ttlSeconds(now, expireAt);

        redis.opsForHash()
                .putAll(
                        sessionKey,
                        Map.of(
                                F_ADMIN_USER_ID,
                                Long.toString(adminUserId),
                                F_USERNAME,
                                username,
                                F_REFRESH_HASH,
                                refreshHash,
                                F_ISSUED_AT,
                                Long.toString(now.toEpochMilli()),
                                F_EXPIRE_AT,
                                Long.toString(expireAt.toEpochMilli())));
        redis.expire(sessionKey, ttlSecs, TimeUnit.SECONDS);

        redis.opsForSet().add(AdminAuthRedisKeyConstants.userSessionsKey(adminUserId), sessionId);

        redis.opsForValue()
                .set(AdminAuthRedisKeyConstants.refreshIndexKey(refreshHash), sessionId, ttlSecs, TimeUnit.SECONDS);
    }

    @Override
    public RotateRefreshDto rotate(String incomingRefreshPlain) {
        Instant now = Instant.now();
        String hIn = RefreshTokenHashUtils.sha256Hex(incomingRefreshPlain);

        String sid = redis.opsForValue().get(AdminAuthRedisKeyConstants.refreshIndexKey(hIn));
        if (sid == null) {
            String replaySid = redis.opsForValue().get(AdminAuthRedisKeyConstants.refreshReplayKey(hIn));
            if (replaySid != null) {
                log.warn("Admin refresh replay detected (Redis), revoking session {}", replaySid);
                purgeSession(replaySid);
                redis.delete(AdminAuthRedisKeyConstants.refreshReplayKey(hIn));
                return new RotateRefreshDto.ReplayRevoked();
            }
            return new RotateRefreshDto.Invalid();
        }

        String sessionKey = AdminAuthRedisKeyConstants.sessionKey(sid);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (raw.isEmpty()) {
            redis.delete(AdminAuthRedisKeyConstants.refreshIndexKey(hIn));
            return new RotateRefreshDto.Invalid();
        }

        String storedHash = Objects.toString(raw.get(F_REFRESH_HASH), "");
        if (!storedHash.equals(hIn)) {
            return new RotateRefreshDto.Invalid();
        }

        String expireMillisStr = Objects.toString(raw.get(F_EXPIRE_AT), "");
        Instant expireAt = Instant.ofEpochMilli(Long.parseLong(expireMillisStr));
        if (now.isAfter(expireAt)) {
            purgeSession(sid);
            return new RotateRefreshDto.Invalid();
        }

        long adminUserId = Long.parseLong(Objects.toString(raw.get(F_ADMIN_USER_ID)));
        String username = Objects.toString(raw.get(F_USERNAME));

        long ttlRemain = ttlSeconds(now, expireAt);

        String newPlain = RefreshTokenUtils.generateOpaque();
        String hNew = RefreshTokenHashUtils.sha256Hex(newPlain);

        redis.delete(AdminAuthRedisKeyConstants.refreshIndexKey(hIn));
        redis.opsForValue()
                .set(AdminAuthRedisKeyConstants.refreshReplayKey(hIn), sid, ttlRemain, TimeUnit.SECONDS);

        redis.opsForHash().put(sessionKey, F_REFRESH_HASH, hNew);
        redis.expire(sessionKey, ttlRemain, TimeUnit.SECONDS);

        redis.opsForValue()
                .set(AdminAuthRedisKeyConstants.refreshIndexKey(hNew), sid, ttlRemain, TimeUnit.SECONDS);

        return new RotateRefreshDto.Rotated(adminUserId, username, sid, newPlain, expireAt);
    }

    @Override
    public void revokeByRefreshCookie(Optional<String> refreshPlain) {
        if (refreshPlain.isEmpty() || refreshPlain.get().isBlank()) {
            return;
        }
        String h = RefreshTokenHashUtils.sha256Hex(refreshPlain.get());

        String sid = redis.opsForValue().get(AdminAuthRedisKeyConstants.refreshIndexKey(h));
        if (sid != null) {
            purgeSession(sid);
            redis.delete(AdminAuthRedisKeyConstants.refreshIndexKey(h));
            return;
        }

        String replaySid = redis.opsForValue().get(AdminAuthRedisKeyConstants.refreshReplayKey(h));
        if (replaySid != null) {
            purgeSession(replaySid);
            redis.delete(AdminAuthRedisKeyConstants.refreshReplayKey(h));
        }
    }

    private void purgeSession(String sessionId) {
        String sessionKey = AdminAuthRedisKeyConstants.sessionKey(sessionId);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (!raw.isEmpty()) {
            long adminUserId = Long.parseLong(Objects.toString(raw.get(F_ADMIN_USER_ID)));
            String refreshHash = Objects.toString(raw.get(F_REFRESH_HASH));
            redis.opsForSet().remove(AdminAuthRedisKeyConstants.userSessionsKey(adminUserId), sessionId);
            redis.delete(AdminAuthRedisKeyConstants.refreshIndexKey(refreshHash));
        }
        redis.delete(sessionKey);
    }

    private static long ttlSeconds(Instant now, Instant expireAt) {
        long secs = Duration.between(now, expireAt).toSeconds();
        return Math.max(1L, secs);
    }
}
