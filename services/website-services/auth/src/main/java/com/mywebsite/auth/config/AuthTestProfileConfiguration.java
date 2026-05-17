package com.mywebsite.auth.config;

import com.mywebsite.auth.service.AdminRefreshSessionService;
import com.mywebsite.auth.service.impl.InMemoryAdminRefreshSessionServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@code test} profile 下在无 Redis、且尚未注册 {@link AdminRefreshSessionService} 时提供内存实现，便于
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=test} 与 CI 冒烟。
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class AuthTestProfileConfiguration {

    @Bean
    @ConditionalOnMissingBean(AdminRefreshSessionService.class)
    public AdminRefreshSessionService inMemoryAdminRefreshSessionService() {
        return new InMemoryAdminRefreshSessionServiceImpl();
    }
}
