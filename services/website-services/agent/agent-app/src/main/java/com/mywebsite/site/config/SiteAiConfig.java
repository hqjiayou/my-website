package com.mywebsite.site.config;

import com.mywebsite.site.properties.SiteAiProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiteAiProperties.class)
public class SiteAiConfig {

    /** SSE 转发线程：每个流式会话占用一个线程直至 DONE/ERROR，用 virtual thread 避免池耗尽。 */
    @Bean(destroyMethod = "shutdown")
    ExecutorService siteAiStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
