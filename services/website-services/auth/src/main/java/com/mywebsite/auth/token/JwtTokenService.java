package com.mywebsite.auth.token;

import com.mywebsite.auth.config.JwtProperties;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.TokenAudience;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtTokenService implements TokenService {

    private final JwtEncoder jwtEncoder;

    private final JwtDecoder jwtDecoder;

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public String createAccessToken(IssuedAccessToken issuance) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString();
        String tokenType = issuance.audience() == TokenAudience.ADMIN ? "ADMIN" : issuance.audience().name();

        Long adminUserId = Long.parseLong(issuance.subjectUserId());

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(issuance.subjectUserId())
                .audience(Collections.singletonList(jwtProperties.getAudience()))
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim(JwtClaimConstants.TOKEN_TYPE, tokenType)
                .claim(JwtClaimConstants.ADMIN_USER_ID, adminUserId)
                .claim(JwtClaimConstants.USERNAME, issuance.username())
                .claim(JwtClaimConstants.SESSION_ID, issuance.sessionId());

        if (!issuance.authorities().isEmpty()) {
            builder.claim(
                    "roles", issuance.authorities().stream().sorted().collect(Collectors.toList()));
        }

        JwtClaimsSet claims = builder.build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    @Override
    public Optional<TokenClaims> parseToken(String token, TokenAudience expectedAudience) {
        Jwt jwt = jwtDecoder.decode(token);

        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.stream().noneMatch(jwtProperties.getAudience()::equals)) {
            return Optional.empty();
        }

        String tokenType = jwt.getClaimAsString(JwtClaimConstants.TOKEN_TYPE);
        if (tokenType == null || !tokenType.equals(expectedAudience.name())) {
            return Optional.empty();
        }

        Object uid = jwt.getClaim(JwtClaimConstants.ADMIN_USER_ID);
        String username = jwt.getClaimAsString(JwtClaimConstants.USERNAME);
        String sessionId = jwt.getClaimAsString(JwtClaimConstants.SESSION_ID);
        if (uid == null || username == null || sessionId == null) {
            return Optional.empty();
        }

        Set<String> authorities = extractRoles(jwt);

        return Optional.of(new TokenClaims(
                expectedAudience,
                jwt.getSubject(),
                username,
                jwt.getId(),
                sessionId,
                tokenType,
                authorities,
                jwt.getIssuedAt(),
                jwt.getExpiresAt()));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
