package com.mywebsite.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentOpenApiConfig {

    @Bean
    public OpenAPI agentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("my-website Agent API")
                        .version("v1")
                        .description("前台站点 API 文档，当前只包含基础框架与健康验证接口。"));
    }
}
