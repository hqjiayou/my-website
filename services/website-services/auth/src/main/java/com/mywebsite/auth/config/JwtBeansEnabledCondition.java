package com.mywebsite.auth.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers symmetric JWT beans only when a non-blank signing secret is present.
 *
 * <p>Rationale: several application modules depend on {@code auth} but must boot without configuring
 * {@code security.jwt.secret} (for example lightweight smoke tests). Admin production/local runs still
 * require the secret — without it the resource server cannot validate tokens.</p>
 */
public final class JwtBeansEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String secret = context.getEnvironment().getProperty("security.jwt.secret");
        return secret != null && !secret.isBlank();
    }
}
