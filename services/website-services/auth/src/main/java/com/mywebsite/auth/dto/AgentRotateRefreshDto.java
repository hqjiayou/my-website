package com.mywebsite.auth.dto;

import com.mywebsite.auth.service.AgentRefreshSessionService;
import java.time.Instant;

/** Agent Refresh 轮换解析结果（仅 {@link AgentRefreshSessionService} 与其调用方内部传递）。 */
public sealed interface AgentRotateRefreshDto {

    record Rotated(
            long agentUserId,
            long agentId,
            String username,
            String sessionId,
            String newRefreshPlain,
            Instant expireAt)
            implements AgentRotateRefreshDto {}

    record Invalid() implements AgentRotateRefreshDto {}

    record ReplayRevoked() implements AgentRotateRefreshDto {}
}
