package com.mywebsite.auth.config;

import com.mywebsite.auth.properties.AgentAuthProperties;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import com.mywebsite.auth.service.impl.RedisAgentAuthRateLimitServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RedisAgentAuthRateLimitConfig {

    @Bean
    @ConditionalOnMissingBean(AgentAuthRateLimitService.class)
    public AgentAuthRateLimitService agentAuthRateLimitService(
            StringRedisTemplate redis, AgentAuthProperties agentAuthProperties) {
        return new RedisAgentAuthRateLimitServiceImpl(redis, agentAuthProperties);
    }
}
