package com.mywebsite.auth;

import com.mywebsite.common.env.LocalDotenvSupport;
import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.properties.AuthJwtKeyProperties;
import com.mywebsite.auth.properties.AuthJwtProperties;
import com.mywebsite.auth.properties.SiteAuthProperties;
import com.mywebsite.common.security.config.TokenProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 统一认证服务：Admin / Site 登录 / Refresh / Logout、Access JWT 签发、Refresh 会话（Redis）。
 *
 * <p>与业务库共用 PostgreSQL 中的 {@code admin_user} / {@code site} / {@code site_user} 表；Flyway 仅在 admin
 * 执行，此处显式关闭以避免重复迁移。</p>
 */
@SpringBootApplication(
        scanBasePackages = {"com.mywebsite.auth", "com.mywebsite.infra"},
        exclude = {FlywayAutoConfiguration.class})
@MapperScan("com.mywebsite.auth.mapper")
@EnableConfigurationProperties({
    AuthJwtProperties.class,
    AdminAuthProperties.class,
    SiteAuthProperties.class,
    AuthJwtKeyProperties.class,
    TokenProperties.class
})
public class AuthApplication {

    public static void main(String[] args) {
        LocalDotenvSupport.tryLoadNearestEnv();
        SpringApplication.run(AuthApplication.class, args);
    }
}
