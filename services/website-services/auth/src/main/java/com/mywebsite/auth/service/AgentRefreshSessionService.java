package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.AgentRotateRefreshDto;
import java.time.Instant;
import java.util.Optional;

/** Agent Refresh 不透明令牌与会话绑定：生产由 Redis 实现；无 Redis 时仅在测试中通过 TestConfiguration 装配内存替身。 */
public interface AgentRefreshSessionService {

    void bindNewSession(
            long agentUserId, long agentId, String username, String sessionId, String refreshPlain, Instant expireAt);

    AgentRotateRefreshDto rotate(String incomingRefreshPlain);

    void revokeByRefreshCookie(Optional<String> refreshPlain);
}
