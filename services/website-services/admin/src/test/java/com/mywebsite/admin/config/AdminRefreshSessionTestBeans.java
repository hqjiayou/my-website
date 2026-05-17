package com.mywebsite.admin.config;

import com.mywebsite.admin.service.AdminRefreshSessionService;
import com.mywebsite.admin.service.impl.InMemoryAdminRefreshSessionServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** 在未装配 Redis 的测试/SpringBootTest 场景中提供 Refresh 会话内存替身。 */
@TestConfiguration
public class AdminRefreshSessionTestBeans {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    AdminRefreshSessionService adminRefreshSessionService() {
        return new InMemoryAdminRefreshSessionServiceImpl();
    }
}
