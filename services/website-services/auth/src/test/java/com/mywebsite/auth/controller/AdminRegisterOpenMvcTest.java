package com.mywebsite.auth.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** 开放注册（仅建议本地）下的唯一约束与 409 行为。 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:auth-bootstrap-register-schema.sql",
            "security.auth.admin-bootstrap-registration-enabled=false",
            "security.auth.admin-open-registration-enabled=true",
        })
@ImportAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
@Transactional
class AdminRegisterOpenMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String u1 = "{\"username\":\"dup_user\",\"password\":\"password99\"}";
        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(u1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(u1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String a =
                "{\"username\":\"u_a\",\"password\":\"password99\",\"email\":\"Same@Example.com\"}";
        String b =
                "{\"username\":\"u_b\",\"password\":\"password99\",\"email\":\"same@example.com\"}";

        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(a))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/admin/register").contentType(APPLICATION_JSON).content(b))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1005));
    }
}
