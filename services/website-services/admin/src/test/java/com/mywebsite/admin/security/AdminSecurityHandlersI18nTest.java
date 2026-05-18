package com.mywebsite.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class AdminSecurityHandlersI18nTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AdminAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private AdminAccessDeniedHandler accessDeniedHandler;

    static Stream<Arguments> missingBearerLocales() {
        return Stream.of(
                Arguments.of("ja-JP", "認証されていません"),
                Arguments.of("zh-CN", "未登录或未完成认证"),
                Arguments.of("en-US", "Authentication is required."));
    }

    @ParameterizedTest
    @MethodSource("missingBearerLocales")
    void unauthorizedWhenNoBearerTokenUsesAuthUnauthorized(String acceptLanguageHeader, String expectedMessage)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        MockHttpServletResponse res = new MockHttpServletResponse();
        authenticationEntryPoint.commence(req, res, Mockito.mock(AuthenticationException.class));
        JsonNode root = OBJECT_MAPPER.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(root.path("code").asInt()).isEqualTo(1002);
        assertThat(root.path("message").asText()).isEqualTo(expectedMessage);
        assertThat(root.path("data").isMissingNode() || root.path("data").isNull()).isTrue();
    }

    static Stream<Arguments> invalidBearerLocales() {
        return Stream.of(
                Arguments.of(
                        "ja-JP",
                        "トークンが無効、または有効期限が切れています",
                        new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Malformed", null))),
                Arguments.of(
                        "zh-CN",
                        "令牌无效或已过期",
                        new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Malformed", null))),
                Arguments.of(
                        "en-US",
                        "The token is invalid or has expired.",
                        new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Malformed", null))));
    }

    @ParameterizedTest
    @MethodSource("invalidBearerLocales")
    void bearerInvalidUsesAuthTokenInvalidLocalized(
            String acceptLanguageHeader, String expectedMessage, AuthenticationException oauth2Ex)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguageHeader);
        MockHttpServletResponse res = new MockHttpServletResponse();
        authenticationEntryPoint.commence(req, res, oauth2Ex);
        JsonNode root = OBJECT_MAPPER.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(root.path("code").asInt()).isEqualTo(1002);
        assertThat(root.path("message").asText()).isEqualTo(expectedMessage);
    }

    @Test
    void accessDeniedHandlerAddsForbiddenCodeAndMessage() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
        MockHttpServletResponse res = new MockHttpServletResponse();
        accessDeniedHandler.handle(req, res, Mockito.mock(AccessDeniedException.class));
        JsonNode root = OBJECT_MAPPER.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(root.path("code").asInt()).isEqualTo(1003);
        assertThat(root.path("message").asText()).isEqualTo("Access was denied.");
        assertThat(root.path("data").isMissingNode() || root.path("data").isNull()).isTrue();
    }
}
