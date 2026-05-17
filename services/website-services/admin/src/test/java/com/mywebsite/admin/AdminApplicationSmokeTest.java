package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;
import com.mywebsite.admin.config.AdminRefreshSessionTestBeans;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.mywebsite.admin.mapper.AdminPermissionMapper;
import com.mywebsite.admin.mapper.AdminRoleMapper;
import com.mywebsite.admin.mapper.AdminRolePermissionMapper;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.mapper.AdminUserRoleMapper;
import com.mywebsite.infra.handler.AuditMetaObjectHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
})
@Import(AdminRefreshSessionTestBeans.class)
class AdminApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsAndRegistersPersistenceBeans() {
        MybatisPlusInterceptor interceptor = applicationContext.getBean(MybatisPlusInterceptor.class);
        assertThat(interceptor.getInterceptors())
                .anyMatch(inner -> inner instanceof OptimisticLockerInnerInterceptor);

        assertThat(applicationContext.getBean(AuditMetaObjectHandler.class)).isNotNull();

        assertThat(applicationContext.getBean(AdminUserMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AdminRoleMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AdminPermissionMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AdminUserRoleMapper.class)).isNotNull();
        assertThat(applicationContext.getBean(AdminRolePermissionMapper.class)).isNotNull();
    }
}
