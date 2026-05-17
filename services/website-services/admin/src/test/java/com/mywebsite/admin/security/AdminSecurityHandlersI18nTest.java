package com.mywebsite.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

class AdminSecurityHandlersI18nTest extends AbstractAdminSpringBootTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AdminAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private AdminAccessDeniedHandler accessDeniedHandler;

    @Test
    void unauthorizedHandlerWritesChineseMessageWhenAcceptLanguageHeaderIsZhCn() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN");
        MockHttpServletResponse res = new MockHttpServletResponse();
        authenticationEntryPoint.commence(req, res, Mockito.mock(AuthenticationException.class));
        JsonNode root = OBJECT_MAPPER.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(root.path("code").asInt()).isEqualTo(1002);
        assertThat(root.path("message").asText()).isEqualTo("未认证或令牌无效");
    }

    @Test
    void accessDeniedHandlerWritesEnglishMessageWhenAcceptLanguageHeaderIsEnUs() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
        MockHttpServletResponse res = new MockHttpServletResponse();
        accessDeniedHandler.handle(req, res, Mockito.mock(AccessDeniedException.class));
        JsonNode root = OBJECT_MAPPER.readTree(res.getContentAsString(StandardCharsets.UTF_8));
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(root.path("code").asInt()).isEqualTo(1003);
        assertThat(root.path("message").asText()).isEqualTo("Access was denied.");
    }
}
