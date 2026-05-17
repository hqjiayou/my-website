package com.mywebsite.auth.principal;

import com.mywebsite.auth.token.JwtClaimConstants;
import com.mywebsite.auth.token.TokenAudience;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Helpers for building {@link AuthenticatedUser} instances from validated JWT payloads. */
public final class AuthenticatedUserUtils {

    private AuthenticatedUserUtils() {}

    /** Maps an Admin audience access JWT into {@link AuthenticatedUser} for request-scope context. */
    public static AuthenticatedUser fromAdminAccessJwt(Jwt jwt) {
        Object uidObj = jwt.getClaim(JwtClaimConstants.ADMIN_USER_ID);
        Objects.requireNonNull(uidObj, "admin_user_id claim missing");
        String username = jwt.getClaimAsString(JwtClaimConstants.USERNAME);
        return new AuthenticatedUser(TokenAudience.ADMIN, jwt.getSubject(), username, Set.of());
    }
}
