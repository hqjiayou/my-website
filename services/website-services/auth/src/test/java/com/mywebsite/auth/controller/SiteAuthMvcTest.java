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
import com.mywebsite.auth.entity.SiteEntity;
import com.mywebsite.auth.entity.SiteUserEntity;
import com.mywebsite.auth.mapper.SiteMapper;
import com.mywebsite.auth.mapper.SiteUserMapper;
import com.mywebsite.auth.service.SiteAuthRateLimitService;
import com.mywebsite.auth.service.SiteRefreshCookieService;
import com.mywebsite.auth.service.impl.InMemorySiteAuthRateLimitServiceImpl;
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
class SiteAuthMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SiteRefreshCookieService siteRefreshCookieService;

    @Autowired
    private SiteAuthRateLimitService rateLimitService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private SiteMapper siteMapper;

    @MockBean
    private SiteUserMapper siteUserMapper;

    @BeforeEach
    void stubSiteUser() {
        ((InMemorySiteAuthRateLimitServiceImpl) rateLimitService).clear();

        SiteEntity site = new SiteEntity();
        site.setId(10L);
        site.setSiteCode("demo");
        site.setSiteName("Demo Site");
        site.setSiteType("CAR");
        site.setStatus("ACTIVE");
        site.setCreatedAt(OffsetDateTime.now());
        site.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(siteMapper.selectOne(any())).thenReturn(site);
        Mockito.when(siteMapper.selectById(10L)).thenReturn(site);

        SiteUserEntity user = new SiteUserEntity();
        user.setId(200L);
        user.setSiteId(10L);
        user.setUsername("site_it");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(siteUserMapper.selectOne(any())).thenReturn(user);
        Mockito.when(siteUserMapper.selectById(200L)).thenReturn(user);
        Mockito.when(siteUserMapper.updateById(org.mockito.ArgumentMatchers.any(SiteUserEntity.class)))
                .thenReturn(1);
    }

    @Test
    void loginHappyPathIssuesSiteJwtWithSiteIdAndCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
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
        assertThat(decoded.getAudience()).contains("SITE");
        assertThat(decoded.getSubject()).isEqualTo(SubjectCodes.formatSiteUserSubject(200L));
        assertThat(decoded.getClaimAsString(JwtClaimConstants.SUBJECT_TYPE)).isEqualTo("SITE");
        assertThat(((Number) decoded.getClaim(JwtClaimConstants.SITE_ID)).longValue()).isEqualTo(10L);
        assertNull(decoded.getClaim(JwtClaimConstants.TOKEN_TYPE));

        Cookie refreshCookie = loginResult.getResponse().getCookie(siteRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getName()).isEqualTo("MYWEBSITE_SITE_REFRESH_TOKEN");
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth/site");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
    }

    @Test
    void loginWrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
                                "username", "site_it",
                                "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("站点编码、用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void loginDisabledUserWithWrongPasswordDoesNotRevealDisabled() throws Exception {
        SiteUserEntity disabled = new SiteUserEntity();
        disabled.setId(201L);
        disabled.setSiteId(10L);
        disabled.setUsername("disabled_user");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(siteUserMapper.selectOne(any())).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
                                "username", "disabled_user",
                                "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("站点编码、用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void loginDisabledUserWithCorrectPasswordIsForbidden() throws Exception {
        SiteUserEntity disabled = new SiteUserEntity();
        disabled.setId(201L);
        disabled.setSiteId(10L);
        disabled.setUsername("disabled_user");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(siteUserMapper.selectOne(any())).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
                                "username", "disabled_user",
                                "password", "correct-password"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号当前不可用"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void siteAccessTokenRejectedWhenParsedAsAdminAudience() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
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
        assertThat(tokenService.parseToken(jwt, TokenAudience.SITE)).isPresent();
    }

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(siteRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/site/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String refreshedJwt = OBJECT_MAPPER
                .readTree(refreshResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        Jwt decoded = jwtDecoder.decode(refreshedJwt);
        assertThat(((Number) decoded.getClaim(JwtClaimConstants.SITE_ID)).longValue()).isEqualTo(10L);

        Cookie rotated = refreshResult.getResponse().getCookie(siteRefreshCookieService.refreshCookieName());
        assertThat(rotated).isNotNull();

        mockMvc.perform(post("/api/auth/site/logout").cookie(rotated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/auth/site/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void refreshRevokesSessionWhenUserBecomesDisabled() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/site/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(Map.of(
                                "siteCode", "demo",
                                "username", "site_it",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(siteRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();

        SiteUserEntity disabled = new SiteUserEntity();
        disabled.setId(200L);
        disabled.setSiteId(10L);
        disabled.setUsername("site_it");
        disabled.setPasswordHash(passwordEncoder.encode("correct-password"));
        disabled.setStatus("DISABLED");
        disabled.setCreatedAt(OffsetDateTime.now());
        disabled.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(siteUserMapper.selectById(200L)).thenReturn(disabled);

        mockMvc.perform(post("/api/auth/site/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));

        // 会话已吊销：原 refresh 再用不成功（含 replay 吊销路径）
        mockMvc.perform(post("/api/auth/site/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
