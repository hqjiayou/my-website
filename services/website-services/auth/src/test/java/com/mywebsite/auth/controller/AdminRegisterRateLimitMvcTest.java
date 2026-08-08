package com.mywebsite.auth.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mywebsite.auth.AuthApplication;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import com.mywebsite.auth.service.impl.InMemoryAdminAuthRateLimitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 注册限流 MockMvc（真实 Mapper + H2；阈值调低）。 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:auth-bootstrap-register-schema.sql",
            "security.auth.admin-bootstrap-registration-enabled=true",
            "security.auth.admin-open-registration-enabled=false",
            "security.auth.admin-register-ip-threshold=3",
            "security.auth.admin-register-ip-window-seconds=120",
        })
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
@Transactional
class AdminRegisterRateLimitMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuthRateLimitService rateLimitService;

    @BeforeEach
    void clearRateLimits() {
        ((InMemoryAdminAuthRateLimitServiceImpl) rateLimitService).clear();
    }

    @Test
    void bootstrapFirstRegisterSucceedsUnderThreshold() throws Exception {
        mockMvc.perform(register("bootstrap_first", "10.3.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bootstrap_first"));
    }

    @Test
    void sameIpRegisterAttemptsExceedThresholdThen429() throws Exception {
        // 首次 bootstrap 成功占 1 次；通道关闭后探测仍计数
        mockMvc.perform(register("reg_root", "10.3.0.2")).andExpect(status().isOk());

        mockMvc.perform(register("reg_two", "10.3.0.2")).andExpect(status().isForbidden());
        mockMvc.perform(register("reg_three", "10.3.0.2")).andExpect(status().isForbidden());

        mockMvc.perform(register("reg_four", "10.3.0.2")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.message").value("尝试过于频繁，请稍后再试"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void closedChannelProbesAlsoCountTowardRegisterLimit() throws Exception {
        mockMvc.perform(register("seed_admin", "10.3.0.3")).andExpect(status().isOk());

        // 通道已关闭：403 探测仍递增计数
        mockMvc.perform(register("probe_a", "10.3.0.3")).andExpect(status().isForbidden());
        mockMvc.perform(register("probe_b", "10.3.0.3")).andExpect(status().isForbidden());
        mockMvc.perform(register("probe_c", "10.3.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String username, String clientIp) {
        return post("/api/auth/admin/register")
                .contentType(APPLICATION_JSON)
                .header("X-Forwarded-For", clientIp)
                .content("{\"username\":\"" + username + "\",\"password\":\"password99\"}");
    }
}
