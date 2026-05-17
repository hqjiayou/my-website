package com.mywebsite.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminOpenApiConfig {

    @Bean
    public OpenAPI adminOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "adminBearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Admin Access JWT（Authorization: Bearer）。Refresh Token 仅 HttpOnly Cookie，不由 Swagger 填写。")))
                .info(new Info()
                        .title("my-website Admin API")
                        .version("v1")
                        .description("后台管理 API：认证链路（登录/刷新/登出）与健康探测等。"));
    }
}
