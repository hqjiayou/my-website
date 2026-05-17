package com.mywebsite.auth.token;

import java.util.Objects;
import java.util.Set;

/** Inputs assembled by callers before {@link TokenService#createAccessToken(IssuedAccessToken)} builds JWT claims. */
public record IssuedAccessToken(
        TokenAudience audience,
        String subjectUserId,
        String username,
        String sessionId,
        Set<String> authorities
) {
    public IssuedAccessToken {
        Objects.requireNonNull(audience);
        Objects.requireNonNull(subjectUserId);
        Objects.requireNonNull(username);
        Objects.requireNonNull(sessionId);
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}
