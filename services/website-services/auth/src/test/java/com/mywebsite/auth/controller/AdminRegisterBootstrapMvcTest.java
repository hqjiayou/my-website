package com.mywebsite.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.auth.AuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * -bootstrap 首轮注册（真实 Mapper + H2 schema.sql）；与普通 {@link AdminAuthMvcTest}（Mock Mapper）互补。
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:auth-bootstrap-register-schema.sql",
            "security.auth.admin-bootstrap-registration-enabled=true",
            "security.auth.admin-open-registration-enabled=false",
        })
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
@Transactional
class AdminRegisterBootstrapMvcTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firstAdminRegistersThenBootstrapClosesSecondUser() throws Exception {
        String payload1 =
                """
                {"username":"bootstrap_root","password":"password99","displayName":"Root"}\
                """;

        String body =
                mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(payload1))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message").value("success"))
                        .andExpect(jsonPath("$.data.username").value("bootstrap_root"))
                        .andExpect(jsonPath("$.data.id").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode root = OM.readTree(body);
        assertThat(root.path("data").path("id").asLong()).isPositive();

        String payload2 =
                """
                {"username":"other_admin","password":"password99"}\
                """;

        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(payload2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void registerThenLoginWritesLastLogin() throws Exception {
        String reg =
                """
                {"username":"login_audit_user","password":"password99","displayName":"A"}\
                """;

        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(reg))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/auth/admin/login")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"login_audit_user\",\"password\":\"password99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }
}
