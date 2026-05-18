package com.mywebsite.admin.config;

import com.mywebsite.common.security.SubjectType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Resource Server：JWKS 验签；校验 {@code iss} / {@code aud} / {@code subject_type=ADMIN}。
 */
@Configuration
public class AdminJwtDecoderConfig {

    @Bean
    JwtDecoder adminJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience =
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        OAuth2TokenValidator<Jwt> subjectAdmin =
                new JwtClaimValidator<>(
                        "subject_type", sub -> SubjectType.ADMIN.name().equals(sub));
        OAuth2TokenValidator<Jwt> combined =
                new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience, subjectAdmin);
        decoder.setJwtValidator(combined);
        return decoder;
    }
}
