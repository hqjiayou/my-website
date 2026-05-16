package com.mywebsite.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminOpenApiConfig {

    @Bean
    public OpenAPI adminOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("my-website Admin API")
                        .version("v1")
                        .description("后台管理 API 文档，当前只包含基础框架与健康验证接口。"));
    }
}
