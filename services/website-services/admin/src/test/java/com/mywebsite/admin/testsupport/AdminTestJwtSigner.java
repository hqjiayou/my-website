package com.mywebsite.admin.testsupport;

import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.SubjectType;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** 测试用 RS256 签发（与 Auth 测试 PEM / kid 对齐），仅用于 admin 模块测试代码。 */
public final class AdminTestJwtSigner {

    private AdminTestJwtSigner() {}

    public static RSAKey loadRsaFromClasspathPrivatePem(String classpathPath) throws Exception {
        try (var in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + classpathPath);
            }
            String pem = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
            if (!(jwk instanceof RSAKey rsa)) {
                throw new IllegalStateException("Expected RSA key in " + classpathPath);
            }
            return rsa;
        }
    }

    public static String signAdminAccess(RSAKey privateKeyJwk, String issuer, String audience) throws Exception {
        return signAdminAccess(privateKeyJwk, issuer, audience, 100L);
    }

    public static String signAdminAccess(RSAKey privateKeyJwk, String issuer, String audience, long adminUserId)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(SubjectCodes.formatAdminSubject(adminUserId))
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim(JwtClaimConstants.SUBJECT_TYPE, SubjectType.ADMIN.name())
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(AdminJwksTestFixture.KEY_ID)
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(privateKeyJwk.toRSAPrivateKey()));
        return jwt.serialize();
    }

    public static String signAgentAccess(
            RSAKey privateKeyJwk, String issuer, String audience, long agentUserId, long agentId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(SubjectCodes.formatAgentUserSubject(agentUserId))
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim(JwtClaimConstants.SUBJECT_TYPE, SubjectType.AGENT.name())
                .claim(JwtClaimConstants.AGENT_ID, agentId)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(AdminJwksTestFixture.KEY_ID)
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(privateKeyJwk.toRSAPrivateKey()));
        return jwt.serialize();
    }
}
