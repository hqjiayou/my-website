package com.mywebsite.auth.dto;

import com.mywebsite.auth.service.AdminRefreshSessionService;
import java.time.Instant;

/** Refresh 轮换解析结果（仅 {@link AdminRefreshSessionService} 与其调用方内部传递）。 */
public sealed interface RotateRefreshDto {

    record Rotated(long adminUserId, String username, String sessionId, String newRefreshPlain, Instant expireAt)
            implements RotateRefreshDto {}

    record Invalid() implements RotateRefreshDto {}

    record ReplayRevoked() implements RotateRefreshDto {}
}
