package com.mywebsite.auth.service;

/** Agent 登录 / 刷新限流（维度含 agentCode）。 */
public interface AgentAuthRateLimitService {

    boolean loginAllowed(String clientIp, String agentCodeNorm, String usernameNorm);

    void recordLoginFailure(String clientIp, String agentCodeNorm, String usernameNorm);

    void resetLoginFailures(String clientIp, String agentCodeNorm, String usernameNorm);

    boolean refreshAllowed(String clientIp);

    void recordRefreshFailure(String clientIp);
}
