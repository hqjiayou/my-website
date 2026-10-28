package com.mywebsite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.entity.AgentEntity;
import com.mywebsite.auth.entity.AgentUserEntity;
import com.mywebsite.auth.mapper.AgentMapper;
import com.mywebsite.auth.mapper.AgentUserMapper;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import com.mywebsite.auth.service.AgentRefreshCookieService;
import com.mywebsite.auth.service.impl.InMemoryAgentAuthRateLimitServiceImpl;
import com.mywebsite.common.security.JwtClaimConstants;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class AgentAuthMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AgentRefreshCookieService agentRefreshCookieService;

    @Autowired
    private AgentAuthRateLimitService rateLimitService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private AgentMapper agentMapper;

    @MockBean
    private AgentUserMapper agentUserMapper;

    @BeforeEach
    void stubAgentUser() {
        ((InMemoryAgentAuthRateLimitServiceImpl) rateLimitService).clear();

        AgentEntity agent = new AgentEntity();
        agent.setId(10L);
        agent.setAgentCode("demo");
        agent.setAgentName("Demo Agent");
        agent.setAgentType("CAR");
        agent.setStatus("ACTIVE");
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(agentMapper.selectOne(any())).thenReturn(agent);
        Mockito.when(agentMapper.selectById(10L)).thenReturn(agent);

        AgentUserEntity user = new AgentUserEntity();
        user.setId(200L);
        user.setAgentId(10L);
        user.setUsername("site_it");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(agentUserMapper.selectOne(any())).thenReturn(user);
        Mockito.when(agentUserMapper.selectById(200L)).thenReturn(user);
        Mockito.when(agentUserMapper.updateById(org.mockito.ArgumentMatchers.any(AgentUserEntity.class)))
                .thenReturn(1);
    }

    @Test
    void loginHappyPathIssuesAgentJwtWithAgentIdAndCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
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
        assertThat(decoded.getAudience()).contains("AGENT");
        assertThat(decoded.getSubject()).isEqualTo(SubjectCodes.formatAgentUserSubject(200L));
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SUBJECT_TYPE)).isEqualTo("AGENT");
        assertThat(((Number) decoded.getClaim(JwtClaimConstants.AGENT_ID)).longValue()).isEqualTo(10L);
        assertNull(decoded.getClaim(JwtClaimConstants.TOKEN_TYPE));

        Cookie refreshCookie = loginResult.getResponse().getCookie(agentRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getName()).isEqualTo("MYWEBSITE_SITE_REFRESH_TOKEN");
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth/agent");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
    }

    @Test
    void loginWrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "site_it",
                                "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("站点编码、用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void loginDisabledUserWithWrongPasswordDoesNotRevealDisabled() throws Exception {
        AgentUserEntity disabled = new AgentUserEntity();
        disabled.setId(201L);
        disabled.setAgentId(10L);
        disabled.setUsername("disabled_user");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(agentUserMapper.selectOne(any())).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "disabled_user",
                                "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("站点编码、用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void loginDisabledUserWithCorrectPasswordIsForbidden() throws Exception {
        AgentUserEntity disabled = new AgentUserEntity();
        disabled.setId(201L);
        disabled.setAgentId(10L);
        disabled.setUsername("disabled_user");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(agentUserMapper.selectOne(any())).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "disabled_user",
                                "password", "correct-password"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号当前不可用"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void agentAccessTokenRejectedWhenParsedAsAdminAudience() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andReturn();

        String jwt = OBJECT_MAPPER
                .readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        assertThat(tokenService.parseToken(jwt, TokenAudience.ADMIN)).isEmpty();
        assertThat(tokenService.parseToken(jwt, TokenAudience.AGENT)).isPresent();
    }

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(agentRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/agent/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String refreshedJwt = OBJECT_MAPPER
                .readTree(refreshResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        Jwt decoded = jwtDecoder.decode(refreshedJwt);
        assertThat(((Number) decoded.getClaim(JwtClaimConstants.AGENT_ID)).longValue()).isEqualTo(10L);

        Cookie rotated = refreshResult.getResponse().getCookie(agentRefreshCookieService.refreshCookieName());
        assertThat(rotated).isNotNull();

        mockMvc.perform(post("/api/auth/agent/logout").cookie(rotated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/auth/agent/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void refreshRevokesSessionWhenUserBecomesDisabled() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/agent/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "agentCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(agentRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();

        AgentUserEntity disabled = new AgentUserEntity();
        disabled.setId(200L);
        disabled.setAgentId(10L);
        disabled.setUsername("site_it");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(agentUserMapper.selectById(200L)).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/agent/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));

        // 会话已吊销：原 refresh 再用不成功（含 replay 吊销路径）
        mockMvc.perform(post("/api/auth/agent/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
