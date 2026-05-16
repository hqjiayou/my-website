package com.mywebsite.site;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.mywebsite.infra.handler.AuditMetaObjectHandler;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.mapper.SiteUserMapper;
import com.mywebsite.site.mapper.SiteUserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = SiteApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
})
class SiteApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsAndRegistersPersistenceBeans() {
        MybatisPlusInterceptor interceptor = applicationContext.getBean(MybatisPlusInterceptor.class);
        assertThat(interceptor.getInterceptors())
                .anyMatch(inner -> inner instanceof OptimisticLockerInnerInterceptor);

        assertThat(applicationContext.getBean(AuditMetaObjectHandler.class)).isNotNull();

        assertThat(applicationContext.getBean(SiteMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(SiteUserMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(SiteUserProfileMapper.class)).isNotNull();
    }
}
