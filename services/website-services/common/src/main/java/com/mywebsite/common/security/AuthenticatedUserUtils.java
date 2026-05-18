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
        return new AuthenticatedUser(TokenAudience.ADMIN, jwt.getSubject(), null, Set.of());
    }

    /** Maps a SITE audience access JWT into {@link AuthenticatedUser} for request-scope context. */
    public static AuthenticatedUser fromSiteAccessJwt(Jwt jwt) {
        Objects.requireNonNull(jwt.getSubject(), "jwt.sub missing");
        SubjectCodes.parseSiteUserId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("jwt.sub is not a valid site subject code"));
        return new AuthenticatedUser(TokenAudience.SITE, jwt.getSubject(), null, Set.of());
    }
}
