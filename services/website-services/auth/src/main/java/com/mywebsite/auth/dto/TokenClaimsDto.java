package com.mywebsite.auth.dto;

import com.mywebsite.common.security.TokenAudience;
import java.time.Instant;

/** Parsed access JWT（精简载荷：主体编码见 {@code subject} / SubjectCodes）。 */
public record TokenClaimsDto(
        TokenAudience audience,
        String subjectUserCode,
        String jwtId,
        Instant issuedAt,
        Instant expiresAt) {}
