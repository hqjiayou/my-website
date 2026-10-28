package com.mywebsite.agent.config;

import com.mywebsite.agent.properties.AgentAiProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentAiProperties.class)
public class AgentAiConfig {

    /** SSE 转发线程：每个流式会话占用一个线程直至 DONE/ERROR，用 virtual thread 避免池耗尽。 */
    @Bean(destroyMethod = "shutdown")
    ExecutorService agentAiStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
