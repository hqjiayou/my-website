package com.mywebsite.admin.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminAiConfig {

    /** SSE 转发线程：每个预览流占用一个线程直至 DONE/ERROR，用 virtual thread 避免池耗尽。 */
    @Bean(destroyMethod = "shutdown")
    ExecutorService adminAiStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
