package com.mywebsite.auth.constant;

import com.mywebsite.auth.utils.RefreshTokenHashUtils;

/**
 * Redis key 前缀与拼装（Agent Refresh 会话 + 认证限流）。
 *
 * <ul>
 *   <li>{@code auth:agent:session:{sessionId}}</li>
 *   <li>{@code auth:agent:user:sessions:{agentUserId}}</li>
 *   <li>{@code auth:agent:refresh:{sha256(refresh)}}</li>
 *   <li>{@code auth:agent:refresh-replay:{sha256(oldRefresh)}}</li>
 *   <li>{@code auth:agent:rl:login:ipsiteuser:{sha256(ip|agentCode|usernameLower)}}</li>
 *   <li>{@code auth:agent:rl:login:siteuser:{sha256(agentCode|usernameLower)}}</li>
 *   <li>{@code auth:agent:rl:login:siteip:{sha256(agentCode|ip)}}</li>
 *   <li>{@code auth:agent:rl:refresh:ip:{ip}}</li>
 * </ul>
 */
public final class AgentAuthRedisKeyConstants {

    private static final String SESSION_PREFIX = "auth:agent:session:";

    private static final String USER_SESSIONS_PREFIX = "auth:agent:user:sessions:";

    private static final String REFRESH_INDEX_PREFIX = "auth:agent:refresh:";

    private static final String REFRESH_REPLAY_PREFIX = "auth:agent:refresh-replay:";

    private static final String RATE_LIMIT_LOGIN_IP_SITE_USER_PREFIX = "auth:agent:rl:login:ipsiteuser:";

    private static final String RATE_LIMIT_LOGIN_SITE_USER_PREFIX = "auth:agent:rl:login:siteuser:";

    private static final String RATE_LIMIT_LOGIN_SITE_IP_PREFIX = "auth:agent:rl:login:siteip:";

    private static final String RATE_LIMIT_REFRESH_IP_PREFIX = "auth:agent:rl:refresh:ip:";

    private AgentAuthRedisKeyConstants() {}

    public static String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String userSessionsKey(long agentUserId) {
        return USER_SESSIONS_PREFIX + agentUserId;
    }

    public static String refreshIndexKey(String refreshSha256Hex) {
        return REFRESH_INDEX_PREFIX + refreshSha256Hex;
    }

    public static String refreshReplayKey(String refreshSha256Hex) {
        return REFRESH_REPLAY_PREFIX + refreshSha256Hex;
    }

    public static String rateLimitLoginIpAgentUserKey(String clientIp, String agentCodeNorm, String usernameNorm) {
        return RATE_LIMIT_LOGIN_IP_SITE_USER_PREFIX
                + RefreshTokenHashUtils.sha256Hex(clientIp + "|" + agentCodeNorm + "|" + usernameNorm);
    }

    public static String rateLimitLoginAgentUserKey(String agentCodeNorm, String usernameNorm) {
        return RATE_LIMIT_LOGIN_SITE_USER_PREFIX
                + RefreshTokenHashUtils.sha256Hex(agentCodeNorm + "|" + usernameNorm);
    }

    public static String rateLimitLoginAgentIpKey(String agentCodeNorm, String clientIp) {
        return RATE_LIMIT_LOGIN_SITE_IP_PREFIX + RefreshTokenHashUtils.sha256Hex(agentCodeNorm + "|" + clientIp);
    }

    public static String rateLimitRefreshIpKey(String clientIp) {
        return RATE_LIMIT_REFRESH_IP_PREFIX + clientIp;
    }
}
