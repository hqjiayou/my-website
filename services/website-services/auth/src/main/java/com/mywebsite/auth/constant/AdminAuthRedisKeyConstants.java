package com.mywebsite.auth.constant;

/**
 * Redis key 前缀与拼装（Admin Refresh 会话）。
 *
 * <ul>
 *   <li>{@code auth:admin:session:{sessionId}}</li>
 *   <li>{@code auth:admin:user:sessions:{adminUserId}}</li>
 *   <li>{@code auth:admin:refresh:{sha256(refresh)}}</li>
 *   <li>{@code auth:admin:refresh-replay:{sha256(oldRefresh)}}</li>
 * </ul>
 */
public final class AdminAuthRedisKeyConstants {

    private static final String SESSION_PREFIX = "auth:admin:session:";

    private static final String USER_SESSIONS_PREFIX = "auth:admin:user:sessions:";

    private static final String REFRESH_INDEX_PREFIX = "auth:admin:refresh:";

    private static final String REFRESH_REPLAY_PREFIX = "auth:admin:refresh-replay:";

    private AdminAuthRedisKeyConstants() {}

    public static String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String userSessionsKey(long adminUserId) {
        return USER_SESSIONS_PREFIX + adminUserId;
    }

    public static String refreshIndexKey(String refreshSha256Hex) {
        return REFRESH_INDEX_PREFIX + refreshSha256Hex;
    }

    public static String refreshReplayKey(String refreshSha256Hex) {
        return REFRESH_REPLAY_PREFIX + refreshSha256Hex;
    }
}
