package com.mywebsite.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.AdminApplication;
import com.mywebsite.admin.config.AdminRefreshSessionTestBeans;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
@Import(AdminRefreshSessionTestBeans.class)
class AdminSecurityHandlersI18nTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AdminAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private AdminAccessDeniedHandler accessDeniedHandler;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        Mockito.when(adminUserMapper.selectOne(Mockito.any())).thenReturn(user);
    }

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
