package com.mywebsite.auth.config;

import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import com.mywebsite.auth.service.impl.RedisAdminAuthRateLimitServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RedisAdminAuthRateLimitConfig {

    /**
     * 与 {@link RedisAdminRefreshSessionConfig} 相同的注册模式：不用
     * {@code @ConditionalOnBean(StringRedisTemplate.class)}（条件过早求值会永久跳过本 Bean），
     * 改为方法参数注入，由 Spring 创建本 Bean 时再解析依赖。
     */
    @Bean
    @ConditionalOnMissingBean(AdminAuthRateLimitService.class)
    public AdminAuthRateLimitService adminAuthRateLimitService(
            StringRedisTemplate redis, AdminAuthProperties adminAuthProperties) {
        return new RedisAdminAuthRateLimitServiceImpl(redis, adminAuthProperties);
    }
}
