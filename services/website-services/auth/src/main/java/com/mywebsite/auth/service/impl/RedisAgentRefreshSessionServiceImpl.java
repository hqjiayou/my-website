package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.constant.AgentAuthRedisKeyConstants;
import com.mywebsite.auth.dto.AgentRotateRefreshDto;
import com.mywebsite.auth.service.AgentRefreshSessionService;
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

/** 由 {@link com.mywebsite.auth.config.RedisAgentRefreshSessionConfig} 注册为 Bean。 */
public class RedisAgentRefreshSessionServiceImpl implements AgentRefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RedisAgentRefreshSessionServiceImpl.class);

    private static final String F_SITE_USER_ID = "agentUserId";

    private static final String F_SITE_ID = "agentId";

    private static final String F_USERNAME = "username";

    private static final String F_REFRESH_HASH = "refreshTokenHash";

    private static final String F_ISSUED_AT = "issuedAtMillis";

    private static final String F_EXPIRE_AT = "expireAtMillis";

    private final StringRedisTemplate redis;

    public RedisAgentRefreshSessionServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void bindNewSession(
            long agentUserId, long agentId, String username, String sessionId, String refreshPlain, Instant expireAt) {
        Instant now = Instant.now();
        String refreshHash = RefreshTokenHashUtils.sha256Hex(refreshPlain);
        String sessionKey = AgentAuthRedisKeyConstants.sessionKey(sessionId);
        long ttlSecs = ttlSeconds(now, expireAt);

        redis.opsForHash()
                .putAll(
                        sessionKey,
                        Map.of(
                                F_SITE_USER_ID,
                                Long.toString(agentUserId),
                                F_SITE_ID,
                                Long.toString(agentId),
                                F_USERNAME,
                                username,
                                F_REFRESH_HASH,
                                refreshHash,
                                F_ISSUED_AT,
                                Long.toString(now.toEpochMilli()),
                                F_EXPIRE_AT,
                                Long.toString(expireAt.toEpochMilli())));
        redis.expire(sessionKey, ttlSecs, TimeUnit.SECONDS);

        redis.opsForSet().add(AgentAuthRedisKeyConstants.userSessionsKey(agentUserId), sessionId);

        redis.opsForValue()
                .set(AgentAuthRedisKeyConstants.refreshIndexKey(refreshHash), sessionId, ttlSecs, TimeUnit.SECONDS);
    }

    @Override
    public AgentRotateRefreshDto rotate(String incomingRefreshPlain) {
        Instant now = Instant.now();
        String hIn = RefreshTokenHashUtils.sha256Hex(incomingRefreshPlain);

        String sid = redis.opsForValue().get(AgentAuthRedisKeyConstants.refreshIndexKey(hIn));
        if (sid == null) {
            String replaySid = redis.opsForValue().get(AgentAuthRedisKeyConstants.refreshReplayKey(hIn));
            if (replaySid != null) {
                log.warn("Agent refresh replay detected (Redis), revoking session {}", replaySid);
                purgeSession(replaySid);
                redis.delete(AgentAuthRedisKeyConstants.refreshReplayKey(hIn));
                return new AgentRotateRefreshDto.ReplayRevoked();
            }
            return new AgentRotateRefreshDto.Invalid();
        }

        String sessionKey = AgentAuthRedisKeyConstants.sessionKey(sid);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (raw.isEmpty()) {
            redis.delete(AgentAuthRedisKeyConstants.refreshIndexKey(hIn));
            return new AgentRotateRefreshDto.Invalid();
        }

        String storedHash = Objects.toString(raw.get(F_REFRESH_HASH), "");
        if (!storedHash.equals(hIn)) {
            return new AgentRotateRefreshDto.Invalid();
        }

        String expireMillisStr = Objects.toString(raw.get(F_EXPIRE_AT), "");
        Instant expireAt = Instant.ofEpochMilli(Long.parseLong(expireMillisStr));
        if (now.isAfter(expireAt)) {
            purgeSession(sid);
            return new AgentRotateRefreshDto.Invalid();
        }

        long agentUserId = Long.parseLong(Objects.toString(raw.get(F_SITE_USER_ID)));
        long agentId = Long.parseLong(Objects.toString(raw.get(F_SITE_ID)));
        String username = Objects.toString(raw.get(F_USERNAME));

        long ttlRemain = ttlSeconds(now, expireAt);

        String newPlain = RefreshTokenUtils.generateOpaque();
        String hNew = RefreshTokenHashUtils.sha256Hex(newPlain);

        redis.delete(AgentAuthRedisKeyConstants.refreshIndexKey(hIn));
        redis.opsForValue().set(AgentAuthRedisKeyConstants.refreshReplayKey(hIn), sid, ttlRemain, TimeUnit.SECONDS);

        redis.opsForHash().put(sessionKey, F_REFRESH_HASH, hNew);
        redis.expire(sessionKey, ttlRemain, TimeUnit.SECONDS);

        redis.opsForValue().set(AgentAuthRedisKeyConstants.refreshIndexKey(hNew), sid, ttlRemain, TimeUnit.SECONDS);

        return new AgentRotateRefreshDto.Rotated(agentUserId, agentId, username, sid, newPlain, expireAt);
    }

    @Override
    public void revokeByRefreshCookie(Optional<String> refreshPlain) {
        if (refreshPlain.isEmpty() || refreshPlain.get().isBlank()) {
            return;
        }
        String h = RefreshTokenHashUtils.sha256Hex(refreshPlain.get());

        String sid = redis.opsForValue().get(AgentAuthRedisKeyConstants.refreshIndexKey(h));
        if (sid != null) {
            purgeSession(sid);
            redis.delete(AgentAuthRedisKeyConstants.refreshIndexKey(h));
            return;
        }

        String replaySid = redis.opsForValue().get(AgentAuthRedisKeyConstants.refreshReplayKey(h));
        if (replaySid != null) {
            purgeSession(replaySid);
            redis.delete(AgentAuthRedisKeyConstants.refreshReplayKey(h));
        }
    }

    private void purgeSession(String sessionId) {
        String sessionKey = AgentAuthRedisKeyConstants.sessionKey(sessionId);
        Map<Object, Object> raw = redis.opsForHash().entries(sessionKey);
        if (!raw.isEmpty()) {
            long agentUserId = Long.parseLong(Objects.toString(raw.get(F_SITE_USER_ID)));
            String refreshHash = Objects.toString(raw.get(F_REFRESH_HASH));
            redis.opsForSet().remove(AgentAuthRedisKeyConstants.userSessionsKey(agentUserId), sessionId);
            redis.delete(AgentAuthRedisKeyConstants.refreshIndexKey(refreshHash));
        }
        redis.delete(sessionKey);
    }

    private static long ttlSeconds(Instant now, Instant expireAt) {
        long secs = Duration.between(now, expireAt).toSeconds();
        return Math.max(1L, secs);
    }
}
