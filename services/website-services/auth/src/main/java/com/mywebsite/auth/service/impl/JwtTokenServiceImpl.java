package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.TokenClaimsDto;
import com.mywebsite.auth.properties.AuthJwtProperties;
import com.mywebsite.auth.service.TokenService;
import com.mywebsite.common.security.AuthenticatedUserUtils;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.SubjectType;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.common.security.config.TokenProperties;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtTokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;

    private final JwtDecoder jwtDecoder;

    private final AuthJwtProperties authJwtProperties;

    private final TokenProperties tokenProperties;

    private final String jwtKeyId;

    public JwtTokenServiceImpl(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            AuthJwtProperties authJwtProperties,
            TokenProperties tokenProperties,
            String jwtKeyId) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.authJwtProperties = authJwtProperties;
        this.tokenProperties = tokenProperties;
        this.jwtKeyId = jwtKeyId;
    }

    @Override
    public String createAccessToken(IssuedAccessTokenDto issuance) {
        Instant now = Instant.now();
        Instant exp = now.plus(tokenProperties.accessTokenTtl());
        String jti = UUID.randomUUID().toString();

        long numericId = Long.parseLong(issuance.subjectUserId());
        String subject = issuance.audience() == TokenAudience.ADMIN
                ? SubjectCodes.formatAdminSubject(numericId)
                : SubjectCodes.formatSiteUserSubject(numericId);

        SubjectType subjectType = subjectTypeForAudience(issuance.audience());

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(authJwtProperties.getIssuer())
                .subject(subject)
                .audience(Collections.singletonList(audienceForJwt(issuance.audience())))
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim(JwtClaimConstants.SUBJECT_TYPE, subjectType.name());

        // SITE 必须写入 site_id；ADMIN 不得携带，避免跨受众冒充站点上下文。
        if (issuance.audience() == TokenAudience.SITE) {
            claimsBuilder.claim(JwtClaimConstants.SITE_ID, issuance.siteIdOrNull());
        }

        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256).header("kid", jwtKeyId).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private String audienceForJwt(TokenAudience audience) {
        return audience == TokenAudience.ADMIN ? authJwtProperties.getAudience() : authJwtProperties.getSiteAudience();
    }

    @Override
    public Optional<TokenClaimsDto> parseToken(String token, TokenAudience expectedAudience) {
        Jwt jwt = jwtDecoder.decode(token);

        List<String> audiences = jwt.getAudience();
        String expectedAud = audienceForJwt(expectedAudience);
        if (audiences == null || audiences.stream().noneMatch(expectedAud::equals)) {
            return Optional.empty();
        }

        SubjectType expectedSubjectType = subjectTypeForAudience(expectedAudience);
        SubjectType actualSubjectType = parseSubjectTypeClaim(jwt.getClaimAsString(JwtClaimConstants.SUBJECT_TYPE));
        if (actualSubjectType != expectedSubjectType) {
            return Optional.empty();
        }

        boolean subjectOk = switch (expectedAudience) {
            case ADMIN -> SubjectCodes.parseAdminUserId(jwt.getSubject()).isPresent();
            case SITE -> SubjectCodes.parseSiteUserId(jwt.getSubject()).isPresent();
        };
        if (!subjectOk) {
            return Optional.empty();
        }

        if (expectedAudience == TokenAudience.SITE
                && AuthenticatedUserUtils.parsePositiveSiteIdClaim(jwt.getClaim(JwtClaimConstants.SITE_ID))
                        .isEmpty()) {
            return Optional.empty();
        }
        if (expectedAudience == TokenAudience.ADMIN && jwt.getClaim(JwtClaimConstants.SITE_ID) != null) {
            return Optional.empty();
        }

        return Optional.of(new TokenClaimsDto(
                expectedAudience,
                jwt.getSubject(),
                jwt.getId(),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()));
    }

    /** 签发侧 {@link TokenAudience} 与 JWT {@code subject_type}（{@link SubjectType}）的对应关系；解析侧必须一致校验。 */
    private static SubjectType subjectTypeForAudience(TokenAudience audience) {
        return switch (audience) {
            case ADMIN -> SubjectType.ADMIN;
            case SITE -> SubjectType.SITE;
        };
    }

    /** {@code subject_type} 必须是有效的 {@link SubjectType} 名称；未知值视为不匹配。 */
    private static SubjectType parseSubjectTypeClaim(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SubjectType.valueOf(raw.trim());
        } catch (@SuppressWarnings("unused") IllegalArgumentException ex) {
            return null;
        }
    }
}
