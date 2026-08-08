package com.mywebsite.auth.config;

import com.mywebsite.auth.properties.SiteAuthProperties;
import com.mywebsite.auth.service.SiteAuthRateLimitService;
import com.mywebsite.auth.service.impl.RedisSiteAuthRateLimitServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class RedisSiteAuthRateLimitConfig {

    @Bean
    @ConditionalOnMissingBean(SiteAuthRateLimitService.class)
    public SiteAuthRateLimitService siteAuthRateLimitService(
            StringRedisTemplate redis, SiteAuthProperties siteAuthProperties) {
        return new RedisSiteAuthRateLimitServiceImpl(redis, siteAuthProperties);
    }
}
