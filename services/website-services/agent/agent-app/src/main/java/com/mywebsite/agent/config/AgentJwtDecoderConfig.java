package com.mywebsite.agent.config;

import com.mywebsite.common.security.AuthenticatedUserUtils;
import com.mywebsite.common.security.JwtClaimConstants;
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
 * Resource Server：JWKS 验签；校验 {@code iss} / {@code aud} / {@code subject_type=AGENT} /
 * 合法 {@code site_id}。
 */
@Configuration
public class AgentJwtDecoderConfig {

    @Bean
    JwtDecoder agentJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience =
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        OAuth2TokenValidator<Jwt> subjectSite =
                new JwtClaimValidator<>(
                        JwtClaimConstants.SUBJECT_TYPE, sub -> SubjectType.AGENT.name().equals(sub));
        OAuth2TokenValidator<Jwt> withAgentId = new JwtClaimValidator<>(
                JwtClaimConstants.SITE_ID,
                agentId -> AuthenticatedUserUtils.parsePositiveAgentIdClaim(agentId).isPresent());
        OAuth2TokenValidator<Jwt> combined =
                new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience, subjectSite, withAgentId);
        decoder.setJwtValidator(combined);
        return decoder;
    }
}
