package com.mywebsite.auth.constant;

import com.mywebsite.auth.utils.RefreshTokenHashUtils;

/**
 * Redis key 前缀与拼装（Admin Refresh 会话 + 认证限流）。
 *
 * <ul>
 *   <li>{@code auth:admin:session:{sessionId}}</li>
 *   <li>{@code auth:admin:user:sessions:{adminUserId}}</li>
 *   <li>{@code auth:admin:refresh:{sha256(refresh)}}</li>
 *   <li>{@code auth:admin:refresh-replay:{sha256(oldRefresh)}}</li>
 *   <li>{@code auth:admin:rl:login:ipuser:{sha256(ip|usernameLower)}}（TTL 见 security.auth 限流窗口配置）</li>
 *   <li>{@code auth:admin:rl:login:user:{sha256(usernameLower)}}</li>
 *   <li>{@code auth:admin:rl:login:ip:{ip}}</li>
 *   <li>{@code auth:admin:rl:register:ip:{ip}}</li>
 *   <li>{@code auth:admin:rl:refresh:ip:{ip}}</li>
 * </ul>
 */
public final class AdminAuthRedisKeyConstants {

    private static final String SESSION_PREFIX = "auth:admin:session:";

    private static final String USER_SESSIONS_PREFIX = "auth:admin:user:sessions:";

    private static final String REFRESH_INDEX_PREFIX = "auth:admin:refresh:";

    private static final String REFRESH_REPLAY_PREFIX = "auth:admin:refresh-replay:";

    private static final String RATE_LIMIT_LOGIN_IP_USER_PREFIX = "auth:admin:rl:login:ipuser:";

    private static final String RATE_LIMIT_LOGIN_USER_PREFIX = "auth:admin:rl:login:user:";

    private static final String RATE_LIMIT_LOGIN_IP_PREFIX = "auth:admin:rl:login:ip:";

    private static final String RATE_LIMIT_REGISTER_IP_PREFIX = "auth:admin:rl:register:ip:";

    private static final String RATE_LIMIT_REFRESH_IP_PREFIX = "auth:admin:rl:refresh:ip:";

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

    /** username 参与的 key 统一 sha256，避免任意用户输入直接进入 key 空间（长度 / 特殊字符）。 */
    public static String rateLimitLoginIpUserKey(String clientIp, String usernameNorm) {
        return RATE_LIMIT_LOGIN_IP_USER_PREFIX + RefreshTokenHashUtils.sha256Hex(clientIp + "|" + usernameNorm);
    }

    public static String rateLimitLoginUserKey(String usernameNorm) {
        return RATE_LIMIT_LOGIN_USER_PREFIX + RefreshTokenHashUtils.sha256Hex(usernameNorm);
    }

    public static String rateLimitLoginIpKey(String clientIp) {
        return RATE_LIMIT_LOGIN_IP_PREFIX + clientIp;
    }

    public static String rateLimitRegisterIpKey(String clientIp) {
        return RATE_LIMIT_REGISTER_IP_PREFIX + clientIp;
    }

    public static String rateLimitRefreshIpKey(String clientIp) {
        return RATE_LIMIT_REFRESH_IP_PREFIX + clientIp;
    }
}
