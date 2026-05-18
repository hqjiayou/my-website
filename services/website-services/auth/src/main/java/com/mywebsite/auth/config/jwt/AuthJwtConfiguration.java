package com.mywebsite.auth.config.jwt;

import com.mywebsite.auth.dto.AuthJwkSetDocumentDto;
import com.mywebsite.auth.properties.AuthJwtKeyProperties;
import com.mywebsite.auth.properties.AuthJwtProperties;
import com.mywebsite.auth.service.TokenService;
import com.mywebsite.auth.service.impl.JwtTokenServiceImpl;
import com.mywebsite.auth.utils.RsaPemUtils;
import com.mywebsite.common.security.config.TokenProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT（RS256）签发、验签、JWKS 文档等 Bean；与 {@link com.mywebsite.auth.config.AuthSecurityConfig}、
 * OpenAPI 等并列，单独置于 {@code config.jwt} 子包便于浏览。
 */
@Configuration
public class AuthJwtConfiguration {

    @Configuration
    static class JwtBeans {

        @Bean
        @ConditionalOnMissingBean
        RSAKey authSigningRsaJwk(Environment env, AuthJwtKeyProperties keyProperties) {
            String privatePath = nonemptyFirst(
                    keyProperties.getPrivateKeyPath(),
                    env.getProperty("mywebsite.auth.jwt.privateKeyPath"),
                    env.getProperty("mywebsite.auth.jwt.private-key-path"),
                    env.getProperty("AUTH_JWT_PRIVATE_KEY_PATH"));
            String publicPath = nonemptyFirst(
                    keyProperties.getPublicKeyPath(),
                    env.getProperty("mywebsite.auth.jwt.publicKeyPath"),
                    env.getProperty("mywebsite.auth.jwt.public-key-path"),
                    env.getProperty("AUTH_JWT_PUBLIC_KEY_PATH"));
            String keyId = nonemptyFirst(
                    keyProperties.getKeyId(),
                    env.getProperty("mywebsite.auth.jwt.keyId"),
                    env.getProperty("mywebsite.auth.jwt.key-id"),
                    env.getProperty("AUTH_JWT_KEY_ID"));
            RSAPrivateKey privateKey = RsaPemUtils.loadPrivateKeyPem(privatePath);
            RSAPublicKey publicKey = RsaPemUtils.loadPublicKeyPem(publicPath);
            assertMatchingKeyPair(privateKey, publicKey, privatePath, publicPath);
            try {
                return new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyUse(KeyUse.SIGNATURE)
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build RSA JWK for JWT signing", e);
            }
        }

        private static String nonemptyFirst(String... candidates) {
            for (String s : candidates) {
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
            return "";
        }

        @Bean
        JwtEncoder jwtEncoder(RSAKey authSigningRsaJwk) {
            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(authSigningRsaJwk));
            return new NimbusJwtEncoder(jwkSource);
        }

        @Bean
        JwtDecoder jwtDecoder(RSAKey authSigningRsaJwk) {
            try {
                RSAPublicKey publicKey = authSigningRsaJwk.toRSAPublicKey();
                return NimbusJwtDecoder.withPublicKey(publicKey).build();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create JwtDecoder from RSA public key", e);
            }
        }

        @Bean
        TokenService tokenService(
                JwtEncoder jwtEncoder,
                JwtDecoder jwtDecoder,
                AuthJwtProperties authJwtProperties,
                TokenProperties tokenProperties,
                AuthJwtKeyProperties keys,
                Environment env) {
            String kid = nonemptyFirst(
                    keys.getKeyId(),
                    env.getProperty("mywebsite.auth.jwt.keyId"),
                    env.getProperty("mywebsite.auth.jwt.key-id"),
                    env.getProperty("AUTH_JWT_KEY_ID"));
            return new JwtTokenServiceImpl(jwtEncoder, jwtDecoder, authJwtProperties, tokenProperties, kid);
        }

        @Bean
        AuthJwkSetDocumentDto authJwkSetDocument(RSAKey authSigningRsaJwk) {
            try {
                RSAKey pub = authSigningRsaJwk.toPublicJWK();
                return new AuthJwkSetDocumentDto(new JWKSet(pub).toString());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build JWKS document", e);
            }
        }

        private static void assertMatchingKeyPair(
                RSAPrivateKey privateKey, RSAPublicKey publicKey, String privatePath, String publicPath) {
            try {
                byte[] challenge = "pair-check".getBytes(StandardCharsets.UTF_8);
                Signature sign = Signature.getInstance("SHA256withRSA");
                sign.initSign(privateKey);
                sign.update(challenge);
                byte[] signature = sign.sign();

                Signature ver = Signature.getInstance("SHA256withRSA");
                ver.initVerify(publicKey);
                ver.update(challenge);
                if (!ver.verify(signature)) {
                    throw new IllegalStateException(
                            "RSA public key does not match private key (check "
                                    + publicPath
                                    + " vs "
                                    + privatePath
                                    + ")");
                }
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(
                        "RSA public/private key mismatch or invalid PEM (check "
                                + publicPath
                                + " vs "
                                + privatePath
                                + ")",
                        e);
            }
        }
    }
}
