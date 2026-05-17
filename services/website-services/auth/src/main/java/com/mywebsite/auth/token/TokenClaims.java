package com.mywebsite.auth.token;

import java.time.Instant;
import java.util.Set;

/** Parsed Admin access JWT semantics (Site tokens reuse shape where applicable). */
public record TokenClaims(
        TokenAudience audience,
        String subject,
        String username,
        String jwtId,
        String sessionId,
        String tokenType,
        Set<String> authorities,
        Instant issuedAt,
        Instant expiresAt
) {}
