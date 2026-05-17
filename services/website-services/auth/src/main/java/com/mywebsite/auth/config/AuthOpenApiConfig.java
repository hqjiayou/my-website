package com.mywebsite.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthOpenApiConfig {

    @Bean
    OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("my-website Auth Service API")
                        .version("v1")
                        .description(
                                "统一认证服务：Admin 登录 / Refresh / Logout、Access JWT（RS256 + JWKS）、"
                                        + "Refresh HttpOnly Cookie + Redis 会话。"));
    }
}
