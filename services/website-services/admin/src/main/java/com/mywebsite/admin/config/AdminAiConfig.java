package com.mywebsite.admin.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminAiConfig {

    /**
     * AI 后台短任务执行器（virtual thread）：预览 SSE 转发、知识库上传后的异步派生索引等。
     * 每个任务占用一个线程直至结束；勿用于无限阻塞的长轮询以外的无关业务。
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService adminAiStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
