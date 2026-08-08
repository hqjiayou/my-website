package com.mywebsite.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.entity.AdminUserEntity;
import com.mywebsite.auth.mapper.AdminUserMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 逃生阀：admin-rate-limit-enabled=false 时不限流。 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "security.auth.admin-rate-limit-enabled=false",
            "security.auth.admin-login-ip-user-failure-threshold=2",
            "security.auth.admin-login-ip-user-window-seconds=120",
        })
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminAuthRateLimitDisabledMvcTest {

    @Autowired
    private MockMvc mockMvc;

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
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(user);
        Mockito.when(adminUserMapper.selectCount(any())).thenReturn(1L);
        Mockito.when(adminUserMapper.updateById(org.mockito.ArgumentMatchers.any(AdminUserEntity.class)))
                .thenReturn(1);
    }

    @Test
    void failuresBeyondThresholdStillReturn401WhenRateLimitDisabled() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "10.9.9.9")
                            .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.9.9.9")
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());
    }
}
