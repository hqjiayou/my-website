package com.mywebsite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.entity.AdminUserEntity;
import com.mywebsite.auth.mapper.AdminUserMapper;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import com.mywebsite.auth.service.AdminRefreshCookieService;
import com.mywebsite.auth.service.impl.InMemoryAdminAuthRateLimitServiceImpl;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.SubjectCodes;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Map;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
class AdminAuthMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminRefreshCookieService adminRefreshCookieService;

    @Autowired
    private AdminAuthRateLimitService rateLimitService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @BeforeEach
    void stubAdminUser() {
        ((InMemoryAdminAuthRateLimitServiceImpl) rateLimitService).clear();

        AdminUserEntity user = new AdminUserEntity();
        user.setId(100L);
        user.setUsername("admin_it");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setDisplayName("IT Admin");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(user);
        Mockito.when(adminUserMapper.selectCount(any())).thenReturn(1L);
        Mockito.when(adminUserMapper.updateById(org.mockito.ArgumentMatchers.any(AdminUserEntity.class)))
                .thenReturn(1);
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
        var req = post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()));
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
        var req = post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"x\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()));
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
        var req = post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin_it\",\"password\":\"\"}");
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()));
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
        var req = post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("username", "admin_it", "password", tooLongPassword)));
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()));
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
        var req = post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(Map.of("username", tooLongUsername, "password", "x")));
        if (acceptLanguageHeader != null) {
            req = req.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        }
        mockMvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void loginJwtMatchesAuthIssuerAudienceAndStructuredClaims() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String jwt = OBJECT_MAPPER
                .readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        Jwt decoded = jwtDecoder.decode(jwt);
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(decoded.getAudience()).contains("ADMIN");
        assertThat(decoded.getSubject()).isEqualTo(SubjectCodes.formatAdminSubject(100L));
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SUBJECT_TYPE)).isEqualTo("ADMIN");
        assertNull(decoded.getClaim(JwtClaimConstants.TOKEN_TYPE));
        assertNull(decoded.getClaim(JwtClaimConstants.ADMIN_USER_ID));
        assertNull(decoded.getClaim(JwtClaimConstants.USERNAME));
        assertNull(decoded.getClaim(JwtClaimConstants.SESSION_ID));
    }

    @Test
    void refreshWithoutCookieFailsWithUnifiedUnauthorizedEnvelopeAndClearsCookie() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/admin/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("認証されていないかトークンが無効です"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();

        Cookie cleared = res.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isEqualTo(0);
    }

    @Test
    void refreshWithUnknownOpaqueFailsUnauthorized() throws Exception {
        Cookie fake = new Cookie(adminRefreshCookieService.refreshCookieName(), "opaque-token-not-bound-to-session-xx");
        mockMvc.perform(post("/api/auth/admin/refresh").cookie(fake))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void replayingFirstRefreshAfterSuccessfulRotationFails() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie firstRefresh =
                loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(firstRefresh).isNotNull();

        mockMvc.perform(post("/api/auth/admin/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/admin/refresh").cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void logoutIsIdempotentWhenRefreshCookieAbsent() throws Exception {
        mockMvc.perform(post("/api/auth/admin/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unknownUsernameFailureEnvelopeMatchesKnownUserWrongPassword() throws Exception {
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(null);
        MvcResult unknown = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content("{\"username\":\"no_such_user\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();

        stubAdminUser();
        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();

        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");

        Cookie refreshCookie =
                loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/admin/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie rotatedRefresh =
                refreshResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(rotatedRefresh).isNotNull();

        mockMvc.perform(post("/api/auth/admin/logout").cookie(rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/auth/admin/refresh").cookie(rotatedRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
