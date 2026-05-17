package com.mywebsite.auth.config;

import com.mywebsite.auth.token.JwtTokenService;
import com.mywebsite.auth.token.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Public authentication module wiring: JWT properties plus symmetric JWT beans when configured.
 *
 * <p>JWT encoder/decoder beans live in the nested configuration class so they register only when
 * {@code security.jwt.secret} is non-blank — modules such as {@code site-app} can compile and run
 * smoke tests without a signing secret; Admin still requires a secret at runtime for auth.</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    @Configuration
    @Conditional(JwtBeansEnabledCondition.class)
    static class JwtBeans {

        @Bean
        @ConditionalOnMissingBean
        SecretKey jwtSigningKey(JwtProperties jwtProperties) {
            String secret = jwtProperties.getSecret();
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        "security.jwt.secret is required (set SECURITY_JWT_SECRET or equivalent)");
            }
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                        "security.jwt.secret must be at least 32 UTF-8 bytes for HS256");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        }

        @Bean
        JwtEncoder jwtEncoder(SecretKey jwtSigningKey) throws Exception {
            OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSigningKey.getEncoded())
                    .keyID("website-services-admin-hs256")
                    .algorithm(JWSAlgorithm.HS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
            JWKSource<SecurityContext> jwkSource =
                    new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk));
            return new NimbusJwtEncoder(jwkSource);
        }

        @Bean
        JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
            return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        }

        @Bean
        TokenService tokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, JwtProperties jwtProperties) {
            return new JwtTokenService(jwtEncoder, jwtDecoder, jwtProperties);
        }
    }
}
