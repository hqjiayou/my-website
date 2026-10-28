package com.mywebsite.auth.config;

import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.properties.AgentAuthProperties;
import com.mywebsite.auth.service.AdminAuthRateLimitService;
import com.mywebsite.auth.service.AdminRefreshSessionService;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import com.mywebsite.auth.service.AgentRefreshSessionService;
import com.mywebsite.auth.service.impl.InMemoryAdminAuthRateLimitServiceImpl;
import com.mywebsite.auth.service.impl.InMemoryAdminRefreshSessionServiceImpl;
import com.mywebsite.auth.service.impl.InMemoryAgentAuthRateLimitServiceImpl;
import com.mywebsite.auth.service.impl.InMemoryAgentRefreshSessionServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@code test} profile 下在无 Redis、且尚未注册对应接口 Bean 时提供内存实现，便于
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=test} 与 CI 冒烟。
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class AuthTestProfileConfig {

    @Bean
    @ConditionalOnMissingBean(AdminRefreshSessionService.class)
    public AdminRefreshSessionService inMemoryAdminRefreshSessionService() {
        return new InMemoryAdminRefreshSessionServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(AdminAuthRateLimitService.class)
    public AdminAuthRateLimitService inMemoryAdminAuthRateLimitService(AdminAuthProperties adminAuthProperties) {
        return new InMemoryAdminAuthRateLimitServiceImpl(adminAuthProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AgentRefreshSessionService.class)
    public AgentRefreshSessionService inMemoryAgentRefreshSessionService() {
        return new InMemoryAgentRefreshSessionServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(AgentAuthRateLimitService.class)
    public AgentAuthRateLimitService inMemoryAgentAuthRateLimitService(AgentAuthProperties agentAuthProperties) {
        return new InMemoryAgentAuthRateLimitServiceImpl(agentAuthProperties);
    }
}
