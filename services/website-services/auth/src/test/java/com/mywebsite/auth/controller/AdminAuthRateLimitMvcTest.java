package com.mywebsite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.entity.AdminUserEntity;
import com.mywebsite.auth.mapper.AdminUserMapper;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import com.mywebsite.auth.service.AdminRefreshCookieService;
import com.mywebsite.auth.service.impl.InMemoryAdminAuthRateLimitServiceImpl;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 登录 / 刷新限流 MockMvc（test profile 内存限流；阈值调低以加速）。
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "security.auth.admin-login-ip-user-failure-threshold=3",
            "security.auth.admin-login-user-failure-threshold=5",
            "security.auth.admin-login-ip-failure-threshold=8",
            "security.auth.admin-login-ip-user-window-seconds=120",
            "security.auth.admin-login-user-window-seconds=120",
            "security.auth.admin-login-ip-window-seconds=120",
            "security.auth.admin-refresh-ip-failure-threshold=3",
            "security.auth.admin-refresh-ip-window-seconds=120",
        })
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminAuthRateLimitMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminRefreshCookieService adminRefreshCookieService;

    @Autowired
    private AdminAuthRateLimitService rateLimitService;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @BeforeEach
    void setUp() {
        ((InMemoryAdminAuthRateLimitServiceImpl) rateLimitService).clear();
        stubKnownUser("admin_it", "correct-password");
    }

    @Test
    void successfulLoginIsUnaffectedWhenRateLimitEnabled() throws Exception {
        mockMvc.perform(login("admin_it", "correct-password", "10.1.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void sameIpUserFailuresExceedThresholdThen429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("admin_it", "wrong", "10.1.0.2"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.2")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.message").value("尝试过于频繁，请稍后再试"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void cooldownRejectsEvenWithCorrectPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("admin_it", "wrong", "10.1.0.3")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("admin_it", "correct-password", "10.1.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void successfulLoginResetsFailureCounters() throws Exception {
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("admin_it", "correct-password", "10.1.0.4")).andExpect(status().isOk());

        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("admin_it", "wrong", "10.1.0.4"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void differentUsernameSameIpUsesSeparateIpUserBucketsUntilIpThreshold() throws Exception {
        stubKnownUser("alice", "correct-password");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("alice", "wrong", "10.1.0.5")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("alice", "wrong", "10.1.0.5")).andExpect(status().isTooManyRequests());

        stubKnownUser("bob", "correct-password");
        mockMvc.perform(login("bob", "wrong", "10.1.0.5")).andExpect(status().isUnauthorized());

        // 打满 IP 维度（阈值 8）：alice 3 + bob 1 = 4；再 4 次使 IP 计数到 8
        for (int i = 0; i < 4; i++) {
            stubKnownUser("u" + i, "correct-password");
            mockMvc.perform(login("u" + i, "wrong", "10.1.0.5")).andExpect(status().isUnauthorized());
        }
        stubKnownUser("victim", "correct-password");
        mockMvc.perform(login("victim", "correct-password", "10.1.0.5"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void usernameDimensionBlocksAcrossIps() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("admin_it", "wrong", "10.2.0." + i)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("admin_it", "wrong", "10.2.0.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void unknownUsernameAlsoCountsTowardRateLimit() throws Exception {
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(null);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("ghost_user", "whatever", "10.1.0.6")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("ghost_user", "whatever", "10.1.0.6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void refreshFailuresExceedThresholdThen429WhileSuccessDoesNotCount() throws Exception {
        MvcResult loginResult = mockMvc.perform(login("admin_it", "correct-password", "10.1.0.7"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(refresh).isNotNull();

        // 合法 refresh 不计入失败桶
        for (int i = 0; i < 5; i++) {
            MvcResult refreshed = mockMvc.perform(post("/api/auth/admin/refresh")
                            .header("X-Forwarded-For", "10.1.0.7")
                            .cookie(refresh))
                    .andExpect(status().isOk())
                    .andReturn();
            refresh = refreshed.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
            assertThat(refresh).isNotNull();
        }

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/admin/refresh").header("X-Forwarded-For", "10.1.0.8"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/admin/refresh").header("X-Forwarded-For", "10.1.0.8"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void refresh429WithExistingCookieDoesNotClearCookie() throws Exception {
        MvcResult loginResult = mockMvc.perform(login("admin_it", "correct-password", "10.1.0.10"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(refresh).isNotNull();

        String limitedIp = "10.1.0.11";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/admin/refresh").header("X-Forwarded-For", limitedIp))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult limited = mockMvc.perform(post("/api/auth/admin/refresh")
                        .header("X-Forwarded-For", limitedIp)
                        .cookie(refresh))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006))
                .andReturn();

        String cookieName = adminRefreshCookieService.refreshCookieName();
        Cookie setCookie = limited.getResponse().getCookie(cookieName);
        assertThat(setCookie).as("429 must not emit a clearing Set-Cookie").isNull();
        assertThat(limited.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(header -> header.contains(cookieName) && header.toLowerCase().contains("max-age=0"));
    }

    @Test
    void xForwardedForUsesLastHopForBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "1.2.3.4, 9.9.9.9")
                            .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "9.9.9.9")
                        .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private void stubKnownUser(String username, String password) {
        AdminUserEntity user = new AdminUserEntity();
        user.setId(100L);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("IT Admin");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(user);
        Mockito.when(adminUserMapper.selectById(100L)).thenReturn(user);
        Mockito.when(adminUserMapper.selectCount(any())).thenReturn(1L);
        Mockito.when(adminUserMapper.updateById(org.mockito.ArgumentMatchers.any(AdminUserEntity.class)))
                .thenReturn(1);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password, String clientIp) {
        return post("/api/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }
}
