package com.mywebsite.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.mywebsite.infra.handler.AuditMetaObjectHandler;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AgentApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
})
class AgentApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsAndRegistersPersistenceBeans() {
        MybatisPlusInterceptor interceptor = applicationContext.getBean(MybatisPlusInterceptor.class);
        assertThat(interceptor.getInterceptors())
                .anyMatch(inner -> inner instanceof OptimisticLockerInnerInterceptor);

        assertThat(applicationContext.getBean(AuditMetaObjectHandler.class)).isNotNull();

        assertThat(applicationContext.getBean(AgentMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentUserMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AgentUserProfileMapper.class)).isNotNull();
    }
}
