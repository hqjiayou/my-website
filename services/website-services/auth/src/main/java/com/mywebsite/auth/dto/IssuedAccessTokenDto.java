package com.mywebsite.auth.dto;

import com.mywebsite.common.security.TokenAudience;
import java.util.Objects;
import java.util.Set;

/**
 * Inputs assembled by callers before {@link com.mywebsite.auth.service.TokenService#createAccessToken(IssuedAccessTokenDto)}
 * builds JWT claims.
 *
 * <p>{@code agentIdOrNull}：AGENT 必填正向站点 ID；ADMIN 必须为 {@code null}。
 */
public record IssuedAccessTokenDto(
        TokenAudience audience,
        String subjectUserId,
        String username,
        String sessionId,
        Long agentIdOrNull,
        Set<String> authorities) {
    public IssuedAccessTokenDto {
        Objects.requireNonNull(audience);
        Objects.requireNonNull(subjectUserId);
        Objects.requireNonNull(username);
        Objects.requireNonNull(sessionId);
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        if (audience == TokenAudience.AGENT) {
            if (agentIdOrNull == null || agentIdOrNull <= 0L) {
                throw new IllegalArgumentException("AGENT access token requires positive agentId");
            }
        } else if (agentIdOrNull != null) {
            throw new IllegalArgumentException("ADMIN access token must not carry agentId");
        }
    }
}
