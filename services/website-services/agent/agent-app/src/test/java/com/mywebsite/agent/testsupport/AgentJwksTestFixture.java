package com.mywebsite.agent.testsupport;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 与 Auth 测试 profile 一致：classpath {@code jwt/test-public.pem} + kid。 */
public final class AgentJwksTestFixture {

    public static final String KEY_ID = "auth-test-rs256-key-1";

    private AgentJwksTestFixture() {}

    public static String jwksJson() {
        try {
            String pem = readClasspathUtf8("jwt/test-public.pem");
            JWK parsedJwk = JWK.parseFromPEMEncodedObjects(pem);
            if (!(parsedJwk instanceof RSAKey parsed)) {
                throw new IllegalStateException("Expected RSA public key in jwt/test-public.pem");
            }
            RSAKey jwk = new RSAKey.Builder(parsed.toRSAPublicKey())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
            return new JWKSet(jwk).toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build test JWKS JSON", e);
        }
    }

    private static String readClasspathUtf8(String classpathPath) throws Exception {
        try (InputStream in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + classpathPath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
