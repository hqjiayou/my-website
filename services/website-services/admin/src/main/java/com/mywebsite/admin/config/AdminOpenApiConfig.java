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
                                                "Admin Access JWT（Authorization: Bearer）。登录/刷新/登出见独立 Auth 服务；Refresh 仅 HttpOnly Cookie。")))
                .info(new Info()
                        .title("my-website Admin API")
                        .version("v1")
                        .description("后台管理业务 API（Resource Server）；认证入口已上提至 Auth 服务（阶段 4F）。"));
    }
}
