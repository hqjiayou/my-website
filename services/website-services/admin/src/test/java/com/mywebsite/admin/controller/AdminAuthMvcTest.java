package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.AdminApplication;
import com.mywebsite.admin.config.AdminRefreshSessionTestBeans;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.service.AdminRefreshCookieService;
import com.mywebsite.auth.token.IssuedAccessToken;
import com.mywebsite.auth.token.JwtClaimConstants;
import com.mywebsite.auth.token.TokenAudience;
import com.mywebsite.auth.token.TokenService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = AdminApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
@Import(AdminRefreshSessionTestBeans.class)
class AdminAuthMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminRefreshCookieService adminRefreshCookieService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @BeforeEach
    void stubAdminUser() {
        AdminUserEntity user = new AdminUserEntity();
        user.setId(100L);
        user.setUsername("admin_it");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setDisplayName("IT Admin");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(user);
    }

    static Stream<Arguments> loginFailureMessagesByLocale() {
        return Stream.of(
                Arguments.of((String) null, "ユーザー名またはパスワードが正しくありません"),
                Arguments.of("zh-CN", "用户名或密码错误"),
                Arguments.of("en-US", "The username or password is incorrect."));
    }

    @ParameterizedTest
    @MethodSource("loginFailureMessagesByLocale")
    void loginFailureMessageIsLocalizedWithoutChangingCode(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        var req = post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> validationUsernameBlankLocales() {
        return Stream.of(
                Arguments.of((String) null, "ユーザー名は必須です"),
                Arguments.of("zh-CN", "用户名不能为空"),
                Arguments.of("en-US", "Username must not be empty."));
    }

    @ParameterizedTest
    @MethodSource("validationUsernameBlankLocales")
    void loginFailsWhenUsernameBlankMessageIsLocalized(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        var req = post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"x\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> validationPasswordBlankLocales() {
        return Stream.of(
                Arguments.of((String) null, "パスワードは必須です"),
                Arguments.of("zh-CN", "密码不能为空"),
                Arguments.of("en-US", "Password must not be empty."));
    }

    @ParameterizedTest
    @MethodSource("validationPasswordBlankLocales")
    void loginFailsWhenPasswordBlankMessageIsLocalized(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        var req = post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin_it\",\"password\":\"\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> validationPasswordTooLongLocales() {
        return Stream.of(
                Arguments.of((String) null, "パスワードが長さの上限を超えています"),
                Arguments.of("zh-CN", "密码长度超出上限"),
                Arguments.of("en-US", "Password exceeds the maximum allowed length."));
    }

    @ParameterizedTest
    @MethodSource("validationPasswordTooLongLocales")
    void loginFailsWhenPasswordExceedsMaxLengthMessageIsLocalized(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        String tooLongPassword = "a".repeat(129);
        var req = post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("username", "admin_it", "password", tooLongPassword)));
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> validationUsernameTooLongLocales() {
        return Stream.of(
                Arguments.of((String) null, "ユーザー名は128文字以内で入力してください"),
                Arguments.of("zh-CN", "用户名长度不能超过128个字符"),
                Arguments.of("en-US", "Username must not exceed 128 characters."));
    }

    @ParameterizedTest
    @MethodSource("validationUsernameTooLongLocales")
    void loginFailsWhenUsernameExceedsMaxLengthMessageIsLocalized(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        String tooLongUsername = "u".repeat(129);
        var req = post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("username", tooLongUsername, "password", "x")));
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    static Stream<Arguments> unauthorizedPingLocales() {
        return Stream.of(
                Arguments.of("ja-JP", "認証されていないかトークンが無効です"),
                Arguments.of("zh-CN", "未认证或令牌无效"),
                Arguments.of("en-US", "Not authenticated or the token is invalid."));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedPingLocales")
    void unauthenticatedPingMessageIsLocalizedByAcceptLanguageHeader(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    @Test
    void unauthenticatedPingUsesJapaneseWhenAcceptLanguageAbsentOrUnsupportedKoKr() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("認証されていないかトークンが無効です"));
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("認証されていないかトークンが無効です"));
    }

    @Test
    void loginJwtMatchesAuthIssuerAudienceAndStructuredClaims() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String jwt = OBJECT_MAPPER
                .readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        Jwt decoded = jwtDecoder.decode(jwt);
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(decoded.getAudience()).contains("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.TOKEN_TYPE)).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SESSION_ID)).isNotBlank();
        assertThat(decoded.getClaimAsString(JwtClaimConstants.USERNAME)).isEqualTo("admin_it");
        assertThat(Number.class.cast(decoded.getClaim(JwtClaimConstants.ADMIN_USER_ID)).longValue())
                .isEqualTo(100L);
    }

    @Test
    void refreshWithoutCookieFailsWithUnifiedUnauthorizedEnvelopeAndClearsCookie() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("認証されていないかトークンが無効です"))
                .andReturn();

        Cookie cleared = res.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isEqualTo(0);
    }

    @Test
    void refreshWithUnknownOpaqueFailsUnauthorized() throws Exception {
        Cookie fake = new Cookie(adminRefreshCookieService.refreshCookieName(), "opaque-token-not-bound-to-session-xx");
        mockMvc.perform(post("/api/admin/auth/refresh").cookie(fake))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void replayingFirstRefreshAfterSuccessfulRotationFails() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        jakarta.servlet.http.Cookie firstRefresh =
                loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(firstRefresh).isNotNull();

        mockMvc.perform(post("/api/admin/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void logoutIsIdempotentWhenRefreshCookieAbsent() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void tamperedBearerAccessTokenRejectedThroughSecurityEntryPoint() throws Exception {
        String validJwt = tokenService.createAccessToken(
                new IssuedAccessToken(TokenAudience.ADMIN, "100", "admin_it", "sid-verify", Set.of()));
        assertThat(validJwt.length()).isGreaterThan(10);
        int lastDot = validJwt.lastIndexOf('.');
        assertThat(lastDot).isGreaterThan(0);
        String tamperedJwt = validJwt.substring(0, lastDot + 1) + "invalid-signature-placeholder";

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + tamperedJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> {
                    String www = result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE);
                    assertThat(www).contains("invalid_token");
                });
    }

    @Test
    void loginRefreshLogoutAndProtectPing() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");

        jakarta.servlet.http.Cookie refreshCookie =
                loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        MvcResult refreshResult = mockMvc.perform(post("/api/admin/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        jakarta.servlet.http.Cookie rotatedRefresh =
                refreshResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(rotatedRefresh).isNotNull();

        mockMvc.perform(post("/api/admin/auth/logout").cookie(rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/auth/refresh").cookie(rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        String jwt = OBJECT_MAPPER.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
