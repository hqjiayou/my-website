package com.mywebsite.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.auth.config.AuthJwtConfiguration;
import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.TokenClaimsDto;
import com.mywebsite.auth.properties.AuthJwtKeyProperties;
import com.mywebsite.auth.properties.AuthJwtProperties;
import com.mywebsite.auth.service.TokenService;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.TokenAudience;
import com.nimbusds.jwt.SignedJWT;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = JwtTokenServiceImplTest.Empty.class)
@ActiveProfiles("test")
@Import(AuthJwtConfiguration.class)
@EnableConfigurationProperties({AuthJwtProperties.class, AuthJwtKeyProperties.class})
@TestPropertySource(
        properties = {
            "mywebsite.auth.jwt.privateKeyPath=classpath:jwt/test-private.pem",
            "mywebsite.auth.jwt.publicKeyPath=classpath:jwt/test-public.pem",
            "mywebsite.auth.jwt.keyId=auth-test-rs256-key-1",
            "security.jwt.issuer=test-issuer",
            "security.jwt.audience=ADMIN"
        })
class JwtTokenServiceImplTest {

    static class Empty {}

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void roundTripAccessTokenCarriesAdminClaimsAndUsesRs256WithKid() throws Exception {
        String jwt = tokenService.createAccessToken(
                new IssuedAccessTokenDto(TokenAudience.ADMIN, "42", "root", "sid-1", Set.of()));

        SignedJWT parsed = SignedJWT.parse(jwt);
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("auth-test-rs256-key-1");

        Optional<TokenClaimsDto> p = tokenService.parseToken(jwt, TokenAudience.ADMIN);
        assertThat(p).isPresent();
        TokenClaimsDto claims = p.get();
        assertThat(claims.audience()).isEqualTo(TokenAudience.ADMIN);
        assertThat(claims.username()).isEqualTo("root");

        Jwt decoded = jwtDecoder.decode(jwt);
        assertThat(decoded.getClaimAsString(JwtClaimConstants.TOKEN_TYPE)).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SESSION_ID)).isEqualTo("sid-1");
        Object uid = decoded.getClaim(JwtClaimConstants.ADMIN_USER_ID);
        assertThat(uid).isInstanceOf(Number.class);
        assertThat(((Number) uid).longValue()).isEqualTo(42L);
    }

    @Test
    void tamperedJwtFailsDecode() {
        String jwt = tokenService.createAccessToken(
                new IssuedAccessTokenDto(TokenAudience.ADMIN, "1", "u", "s", Set.of()));
        String tampered = jwt.substring(0, jwt.length() - 4) + "xxxx";
        assertThatThrownBy(() -> jwtDecoder.decode(tampered))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
