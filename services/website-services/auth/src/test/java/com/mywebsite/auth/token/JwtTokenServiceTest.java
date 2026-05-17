package com.mywebsite.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.auth.config.AuthConfig;
import com.mywebsite.auth.config.JwtProperties;
import com.mywebsite.auth.token.JwtClaimConstants;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {AuthConfig.class})
@TestPropertySource(
        properties = {
            "spring.main.web-application-type=none",
            "security.jwt.secret=test-signing-secret-at-least-32-bytes-long-xx",
            "security.jwt.issuer=test-issuer",
            "security.jwt.audience=ADMIN",
            "security.jwt.access-token-ttl=15m",
        })
class JwtTokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issuesAndParsesAdminAccessToken() {
        String jwt = tokenService.createAccessToken(new IssuedAccessToken(
                TokenAudience.ADMIN, "42", "root", "sid-1", Set.of()));

        Optional<TokenClaims> parsed = tokenService.parseToken(jwt, TokenAudience.ADMIN);
        assertThat(parsed).isPresent();
        TokenClaims claims = parsed.get();
        assertThat(claims.subject()).isEqualTo("42");
        assertThat(claims.username()).isEqualTo("root");
        assertThat(claims.sessionId()).isEqualTo("sid-1");
        assertThat(claims.tokenType()).isEqualTo("ADMIN");
        assertThat(claims.jwtId()).isNotBlank();
        assertThat(claims.audience()).isEqualTo(TokenAudience.ADMIN);
        assertThat(claims.authorities()).isEmpty();
        assertThat(jwtProperties.getIssuer()).isEqualTo("test-issuer");

        Jwt decoded = jwtDecoder.decode(jwt);
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(decoded.getAudience()).contains("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.TOKEN_TYPE)).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SESSION_ID)).isEqualTo("sid-1");
        assertThat(Number.class.cast(decoded.getClaim(JwtClaimConstants.ADMIN_USER_ID)).longValue()).isEqualTo(42L);
    }
}
