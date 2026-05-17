package com.mywebsite.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.auth.config.AuthJwtConfiguration;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.TokenAudience;
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

import com.mywebsite.auth.config.JwtProperties;

@SpringBootTest(classes = JwtTokenServiceTest.Empty.class)
@ActiveProfiles("test")
@Import(AuthJwtConfiguration.class)
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(
        properties = {
            "security.jwt.secret=test-signing-secret-at-least-32-bytes-long-xx",
            "security.jwt.issuer=test-issuer",
            "security.jwt.audience=ADMIN"
        })
class JwtTokenServiceTest {

    static class Empty {}

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void roundTripAccessTokenCarriesAdminClaims() {
        String jwt = tokenService.createAccessToken(
                new IssuedAccessToken(TokenAudience.ADMIN, "42", "root", "sid-1", Set.of()));

        Optional<TokenClaims> parsed = tokenService.parseToken(jwt, TokenAudience.ADMIN);
        assertThat(parsed).isPresent();
        TokenClaims claims = parsed.get();
        assertThat(claims.audience()).isEqualTo(TokenAudience.ADMIN);
        assertThat(claims.username()).isEqualTo("root");

        Jwt decoded = jwtDecoder.decode(jwt);
        assertThat(decoded.getClaimAsString(JwtClaimConstants.TOKEN_TYPE)).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SESSION_ID)).isEqualTo("sid-1");
        assertThat(Number.class.cast(decoded.getClaim(JwtClaimConstants.ADMIN_USER_ID)).longValue()).isEqualTo(42L);
    }
}
