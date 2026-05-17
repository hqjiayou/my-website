package com.mywebsite.auth.dto;

import com.mywebsite.common.security.TokenAudience;
import java.time.Instant;
import java.util.Set;

/** Parsed Admin access JWT semantics (Site tokens reuse shape where applicable). */
public record TokenClaimsDto(
        TokenAudience audience,
        String subject,
        String username,
        String jwtId,
        String sessionId,
        String tokenType,
        Set<String> authorities,
        Instant issuedAt,
        Instant expiresAt) {}
