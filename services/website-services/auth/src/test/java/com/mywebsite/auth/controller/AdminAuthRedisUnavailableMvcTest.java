package com.mywebsite.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.entity.AdminUserEntity;
import com.mywebsite.auth.mapper.AdminUserMapper;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Redis / 限流依赖不可用时 fail-closed：认证入口返回系统错误，不得放行 200。
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminAuthRedisUnavailableMvcTest {

    private static final RedisConnectionFailureException REDIS_DOWN =
            new RedisConnectionFailureException("redis unavailable");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AdminAuthRateLimitService rateLimitService;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @BeforeEach
    void stubRateLimitThrowsAndKnownUser() {
        Mockito.when(rateLimitService.loginAllowed(anyString(), anyString())).thenThrow(REDIS_DOWN);
        Mockito.when(rateLimitService.registerAllowed(anyString())).thenThrow(REDIS_DOWN);
        Mockito.when(rateLimitService.refreshAllowed(anyString())).thenThrow(REDIS_DOWN);

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
        Mockito.when(adminUserMapper.updateById(any(AdminUserEntity.class))).thenReturn(1);
    }

    @Test
    void loginIsUnavailableWhenRedisFails() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.8.0.1")
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void registerIsUnavailableWhenRedisFails() throws Exception {
        mockMvc.perform(post("/api/auth/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.8.0.2")
                        .content("{\"username\":\"new_admin\",\"password\":\"password99\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void refreshIsUnavailableWhenRedisFails() throws Exception {
        Cookie present = new Cookie("MYWEBSITE_REFRESH_TOKEN", "opaque-present-but-redis-down");
        mockMvc.perform(post("/api/auth/admin/refresh")
                        .header("X-Forwarded-For", "10.8.0.3")
                        .cookie(present))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000));
    }
}
