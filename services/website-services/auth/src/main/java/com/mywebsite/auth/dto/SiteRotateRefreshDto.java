package com.mywebsite.auth.dto;

import com.mywebsite.auth.service.SiteRefreshSessionService;
import java.time.Instant;

/** Site Refresh 轮换解析结果（仅 {@link SiteRefreshSessionService} 与其调用方内部传递）。 */
public sealed interface SiteRotateRefreshDto {

    record Rotated(
            long siteUserId,
            long siteId,
            String username,
            String sessionId,
            String newRefreshPlain,
            Instant expireAt)
            implements SiteRotateRefreshDto {}

    record Invalid() implements SiteRotateRefreshDto {}

    record ReplayRevoked() implements SiteRotateRefreshDto {}
}
