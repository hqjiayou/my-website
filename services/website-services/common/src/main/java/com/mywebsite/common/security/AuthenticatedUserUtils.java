package com.mywebsite.common.security;

import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Helpers for building {@link AuthenticatedUser} instances from validated JWT payloads. */
public final class AuthenticatedUserUtils {

    private AuthenticatedUserUtils() {}

    /** Maps an Admin audience access JWT into {@link AuthenticatedUser} for request-scope context. */
    public static AuthenticatedUser fromAdminAccessJwt(Jwt jwt) {
        Objects.requireNonNull(jwt.getSubject(), "jwt.sub missing");
        SubjectCodes.parseAdminUserId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("jwt.sub is not a valid admin subject code"));
        return new AuthenticatedUser(TokenAudience.ADMIN, jwt.getSubject(), null, null, Set.of());
    }

    /** Maps a AGENT audience access JWT into {@link AuthenticatedUser} for request-scope context. */
    public static AuthenticatedUser fromAgentAccessJwt(Jwt jwt) {
        Objects.requireNonNull(jwt.getSubject(), "jwt.sub missing");
        SubjectCodes.parseAgentUserId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("jwt.sub is not a valid agent subject code"));
        Long agentId = parsePositiveAgentIdClaim(jwt.getClaim(JwtClaimConstants.SITE_ID))
                .orElseThrow(() -> new IllegalStateException("jwt.site_id missing or invalid"));
        return new AuthenticatedUser(TokenAudience.AGENT, jwt.getSubject(), null, agentId, Set.of());
    }

    /** 解析 JWT {@code site_id} 声明为正向 Long；非法或缺失返回 empty。 */
    public static java.util.Optional<Long> parsePositiveAgentIdClaim(Object raw) {
        if (raw == null) {
            return java.util.Optional.empty();
        }
        long value;
        if (raw instanceof Number number) {
            value = number.longValue();
        } else if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return java.util.Optional.empty();
            }
            try {
                value = Long.parseLong(trimmed);
            } catch (@SuppressWarnings("unused") NumberFormatException ex) {
                return java.util.Optional.empty();
            }
        } else {
            return java.util.Optional.empty();
        }
        return value > 0 ? java.util.Optional.of(value) : java.util.Optional.empty();
    }
}
