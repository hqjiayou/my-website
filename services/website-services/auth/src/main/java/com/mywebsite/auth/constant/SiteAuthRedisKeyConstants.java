package com.mywebsite.auth.constant;

import com.mywebsite.auth.utils.RefreshTokenHashUtils;

/**
 * Redis key 前缀与拼装（Site Refresh 会话 + 认证限流）。
 *
 * <ul>
 *   <li>{@code auth:site:session:{sessionId}}</li>
 *   <li>{@code auth:site:user:sessions:{siteUserId}}</li>
 *   <li>{@code auth:site:refresh:{sha256(refresh)}}</li>
 *   <li>{@code auth:site:refresh-replay:{sha256(oldRefresh)}}</li>
 *   <li>{@code auth:site:rl:login:ipsiteuser:{sha256(ip|siteCode|usernameLower)}}</li>
 *   <li>{@code auth:site:rl:login:siteuser:{sha256(siteCode|usernameLower)}}</li>
 *   <li>{@code auth:site:rl:login:siteip:{sha256(siteCode|ip)}}</li>
 *   <li>{@code auth:site:rl:refresh:ip:{ip}}</li>
 * </ul>
 */
public final class SiteAuthRedisKeyConstants {

    private static final String SESSION_PREFIX = "auth:site:session:";

    private static final String USER_SESSIONS_PREFIX = "auth:site:user:sessions:";

    private static final String REFRESH_INDEX_PREFIX = "auth:site:refresh:";

    private static final String REFRESH_REPLAY_PREFIX = "auth:site:refresh-replay:";

    private static final String RATE_LIMIT_LOGIN_IP_SITE_USER_PREFIX = "auth:site:rl:login:ipsiteuser:";

    private static final String RATE_LIMIT_LOGIN_SITE_USER_PREFIX = "auth:site:rl:login:siteuser:";

    private static final String RATE_LIMIT_LOGIN_SITE_IP_PREFIX = "auth:site:rl:login:siteip:";

    private static final String RATE_LIMIT_REFRESH_IP_PREFIX = "auth:site:rl:refresh:ip:";

    private SiteAuthRedisKeyConstants() {}

    public static String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String userSessionsKey(long siteUserId) {
        return USER_SESSIONS_PREFIX + siteUserId;
    }

    public static String refreshIndexKey(String refreshSha256Hex) {
        return REFRESH_INDEX_PREFIX + refreshSha256Hex;
    }

    public static String refreshReplayKey(String refreshSha256Hex) {
        return REFRESH_REPLAY_PREFIX + refreshSha256Hex;
    }

    public static String rateLimitLoginIpSiteUserKey(String clientIp, String siteCodeNorm, String usernameNorm) {
        return RATE_LIMIT_LOGIN_IP_SITE_USER_PREFIX
                + RefreshTokenHashUtils.sha256Hex(clientIp + "|" + siteCodeNorm + "|" + usernameNorm);
    }

    public static String rateLimitLoginSiteUserKey(String siteCodeNorm, String usernameNorm) {
        return RATE_LIMIT_LOGIN_SITE_USER_PREFIX
                + RefreshTokenHashUtils.sha256Hex(siteCodeNorm + "|" + usernameNorm);
    }

    public static String rateLimitLoginSiteIpKey(String siteCodeNorm, String clientIp) {
        return RATE_LIMIT_LOGIN_SITE_IP_PREFIX + RefreshTokenHashUtils.sha256Hex(siteCodeNorm + "|" + clientIp);
    }

    public static String rateLimitRefreshIpKey(String clientIp) {
        return RATE_LIMIT_REFRESH_IP_PREFIX + clientIp;
    }
}
