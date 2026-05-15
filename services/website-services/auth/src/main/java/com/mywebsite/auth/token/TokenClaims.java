package com.mywebsite.auth.token;

import java.time.Instant;
import java.util.Set;

public record TokenClaims(
        TokenAudience audience,
        String subject,
        String username,
        Set<String> authorities,
        Instant issuedAt,
        Instant expiresAt
) {
}
