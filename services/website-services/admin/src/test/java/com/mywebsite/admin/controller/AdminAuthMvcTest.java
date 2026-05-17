package com.mywebsite.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.mywebsite.admin.AdminApplication;
import com.mywebsite.admin.config.AdminRefreshSessionTestBeans;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.service.AdminRefreshCookieService;
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
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = AdminApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
@Import(AdminRefreshSessionTestBeans.class)
class AdminAuthMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminRefreshCookieService adminRefreshCookieService;

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
        Mockito.when(adminUserMapper.selectOne(any())).thenReturn(user);
    }

    @Test
    void loginFailureReturnsUnauthorizedWithoutCookieSideChannel() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void loginRefreshLogoutAndProtectPing() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_it\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");

        jakarta.servlet.http.Cookie refreshCookie =
                loginResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        MvcResult refreshResult = mockMvc.perform(post("/api/admin/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        jakarta.servlet.http.Cookie rotatedRefresh =
                refreshResult.getResponse().getCookie(adminRefreshCookieService.refreshCookieName());
        assertThat(rotatedRefresh).isNotNull();

        mockMvc.perform(post("/api/admin/auth/logout").cookie(rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/admin/auth/refresh").cookie(rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String jwt = mapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();

        mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
