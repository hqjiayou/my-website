package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.SiteAuthRedisKeyConstants;
import com.mywebsite.auth.dto.SiteRotateRefreshDto;
import com.mywebsite.auth.service.SiteRefreshSessionService;
import com.mywebsite.auth.utils.RefreshTokenHashUtils;
import com.mywebsite.auth.utils.RefreshTokenUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 由 {@link com.mywebsite.auth.config.RedisSiteRefreshSessionConfig} 注册为 Bean。 */
public class RedisSiteRefreshSessionServiceImpl implements SiteRefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RedisSiteRefreshSessionServiceImpl.class);

    private static final String F_SITE_USER_ID = "siteUserId";

    private static final String F_SITE_ID = "siteId";

    private static final String F_USERNAME = "username";

    private static final String F_REFRESH_HASH = "refreshTokenHash";

    private static final String F_ISSUED_AT = "issuedAtMillis";

    private static final String F_EXPIRE_AT = "expireAtMillis";

    private final StringRedisTemplate redis;

    public RedisSiteRefreshSessionServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void bindNewSession(
            long siteUserId, long siteId, String username, String sessionId, String refreshPlain, Instant expireAt) {
        Instant now = Instant.now();
        String refreshHash = RefreshTokenHashUtils.sha256Hex(refreshPlain);
        String sessionKey = SiteAuthRedisKeyConstants.sessionKey(sessionId);
        long ttlSecs = ttlSeconds(now, expireAt);

        redis.opsForHash()
                .putAll(
                        sessionKey,
                        Map.of(
                                F_SITE_USER_ID,
                                Long.toString(siteUserId),
                                F_SITE_ID,
                                Long.toString(siteId),
                                F_USERNAME,
                                username,
                                F_REFRESH_HASH,
                                refreshHash,
                                F_ISSUED_AT,
                                Long.toString(now.toEpochMilli()),
                                F_EXPIRE_AT,
                                Long.toString(expireAt.toEpochMilli())));
        redis.expire(sessionKey, ttlSecs, TimeUnit.SECONDS);

        redis.opsForSet().add(SiteAuthRedisKeyConstants.userSessionsKey(siteUserId), sessionId);

        redis.opsForValue()
                .set(SiteAuthRedisKeyConstants.refreshIndexKey(refreshHash), sessionId, ttlSecs, TimeUnit.SECONDS);
    }

    @Override
    public SiteRotateRefreshDto rotate(String incomingRefreshPlain) {
        Instant now = Instant.now();
        String hIn = RefreshTokenHashUtils.sha256Hex(incomingRefreshPlain);

        String sid = redis.opsForValue().get(SiteAuthRedisKeyConstants.refreshIndexKey(hIn));
        if (sid == null) {
            String replaySid = redis.opsForValue().get(SiteAuthRedisKeyConstants.refreshReplayKey(hIn));
            if (replaySid != null) {
                log.warn("Site refresh replay detected (Redis), revoking session {}", replaySid);
                purgeSession(replaySid);
                redis.delete(SiteAuthRedisKeyConstants.refreshReplayKey(hIn));
                return new SiteRotateRefreshDto.ReplayRevoked();
            }
            return new SiteRotateRefreshDto.Invalid();
        }

        String sessionKey = SiteAuthRedisKeyConstants.sessionKey(sid);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (raw.isEmpty()) {
            redis.delete(SiteAuthRedisKeyConstants.refreshIndexKey(hIn));
            return new SiteRotateRefreshDto.Invalid();
        }

        String storedHash = Objects.toString(raw.get(F_REFRESH_HASH), "");
        if (!storedHash.equals(hIn)) {
            return new SiteRotateRefreshDto.Invalid();
        }

        String expireMillisStr = Objects.toString(raw.get(F_EXPIRE_AT), "");
        Instant expireAt = Instant.ofEpochMilli(Long.parseLong(expireMillisStr));
        if (now.isAfter(expireAt)) {
            purgeSession(sid);
            return new SiteRotateRefreshDto.Invalid();
        }

        long siteUserId = Long.parseLong(Objects.toString(raw.get(F_SITE_USER_ID)));
        long siteId = Long.parseLong(Objects.toString(raw.get(F_SITE_ID)));
        String username = Objects.toString(raw.get(F_USERNAME));

        long ttlRemain = ttlSeconds(now, expireAt);

        String newPlain = RefreshTokenUtils.generateOpaque();
        String hNew = RefreshTokenHashUtils.sha256Hex(newPlain);

        redis.delete(SiteAuthRedisKeyConstants.refreshIndexKey(hIn));
        redis.opsForValue().set(SiteAuthRedisKeyConstants.refreshReplayKey(hIn), sid, ttlRemain, TimeUnit.SECONDS);

        redis.opsForHash().put(sessionKey, F_REFRESH_HASH, hNew);
        redis.expire(sessionKey, ttlRemain, TimeUnit.SECONDS);

        redis.opsForValue().set(SiteAuthRedisKeyConstants.refreshIndexKey(hNew), sid, ttlRemain, TimeUnit.SECONDS);

        return new SiteRotateRefreshDto.Rotated(siteUserId, siteId, username, sid, newPlain, expireAt);
    }

    @Override
    public void revokeByRefreshCookie(Optional<String> refreshPlain) {
        if (refreshPlain.isEmpty() || refreshPlain.get().isBlank()) {
            return;
        }
        String h = RefreshTokenHashUtils.sha256Hex(refreshPlain.get());

        String sid = redis.opsForValue().get(SiteAuthRedisKeyConstants.refreshIndexKey(h));
        if (sid != null) {
            purgeSession(sid);
            redis.delete(SiteAuthRedisKeyConstants.refreshIndexKey(h));
            return;
        }

        String replaySid = redis.opsForValue().get(SiteAuthRedisKeyConstants.refreshReplayKey(h));
        if (replaySid != null) {
            purgeSession(replaySid);
            redis.delete(SiteAuthRedisKeyConstants.refreshReplayKey(h));
        }
    }

    private void purgeSession(String sessionId) {
        String sessionKey = SiteAuthRedisKeyConstants.sessionKey(sessionId);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (!raw.isEmpty()) {
            long siteUserId = Long.parseLong(Objects.toString(raw.get(F_SITE_USER_ID)));
            String refreshHash = Objects.toString(raw.get(F_REFRESH_HASH));
            redis.opsForSet().remove(SiteAuthRedisKeyConstants.userSessionsKey(siteUserId), sessionId);
            redis.delete(SiteAuthRedisKeyConstants.refreshIndexKey(refreshHash));
        }
        redis.delete(sessionKey);
    }

    private static long ttlSeconds(Instant now, Instant expireAt) {
        long secs = Duration.between(now, expireAt).toSeconds();
        return Math.max(1L, secs);
    }
}
