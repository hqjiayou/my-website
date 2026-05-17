package com.mywebsite.auth.dto;

import com.mywebsite.common.security.TokenAudience;
import java.util.Objects;
import java.util.Set;

/**
 * Inputs assembled by callers before {@link com.mywebsite.auth.service.TokenService#createAccessToken(IssuedAccessTokenDto)}
 * builds JWT claims.
 */
public record IssuedAccessTokenDto(
        TokenAudience audience,
        String subjectUserId,
        String username,
        String sessionId,
        Set<String> authorities) {
    public IssuedAccessTokenDto {
        Objects.requireNonNull(audience);
        Objects.requireNonNull(subjectUserId);
        Objects.requireNonNull(username);
        Objects.requireNonNull(sessionId);
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}
