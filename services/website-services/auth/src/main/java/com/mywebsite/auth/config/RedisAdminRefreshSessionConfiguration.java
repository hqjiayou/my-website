package com.mywebsite.auth.config;

import com.mywebsite.auth.service.AdminRefreshSessionService;
import com.mywebsite.auth.service.impl.RedisAdminRefreshSessionServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RedisAdminRefreshSessionConfiguration {

    /**
     * 不使用 {@code @ConditionalOnBean(StringRedisTemplate.class)}：该条件会在配置类解析阶段过早求值，可能早于
     * {@code RedisAutoConfiguration} 注册 {@link StringRedisTemplate}，导致本 Bean 被永久跳过。改为通过方法参数注入
     * {@link StringRedisTemplate}，由 Spring 在创建本 Bean 时再解析依赖。
     */
    @Bean
    @ConditionalOnMissingBean(AdminRefreshSessionService.class)
    public AdminRefreshSessionService adminRefreshSessionService(StringRedisTemplate redis) {
        return new RedisAdminRefreshSessionServiceImpl(redis);
    }
}
