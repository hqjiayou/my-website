package com.mywebsite.infra.jwt;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.PlaceholderResolutionException;

/**
 * True when a non-blank HS256 JWT secret is available via env or {@code security.jwt.secret}.
 * Used to register symmetric JWT beans only when configured (smoke tests can omit the secret).
 */
public final class JwtSecretPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        if (isNonBlank(env.getProperty("SECURITY_JWT_SECRET"))) {
            return true;
        }
        try {
            return isNonBlank(env.getProperty("security.jwt.secret"));
        } catch (PlaceholderResolutionException ex) {
            return false;
        }
    }

    private static boolean isNonBlank(String v) {
        return v != null && !v.isBlank();
    }
}
