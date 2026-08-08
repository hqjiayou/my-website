package com.mywebsite.auth.config;

import com.mywebsite.auth.properties.AdminAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

/**
 * 启动期高风险配置守卫：开放注册（非 bootstrap 自助注册）开启时输出显著告警。
 *
 * <p>当前无可靠的生产 profile 判据，故只告警不 fail-fast。
 */
@Configuration(proxyBeanMethods = false)
public class AdminAuthStartupGuardConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthStartupGuardConfig.class);

    private final AdminAuthProperties adminAuthProperties;

    public AdminAuthStartupGuardConfig(AdminAuthProperties adminAuthProperties) {
        this.adminAuthProperties = adminAuthProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!adminAuthProperties.isAdminOpenRegistrationEnabled()) {
            return;
        }
        log.warn("""

                ================================================================================
                !! 高风险配置：security.auth.admin-open-registration-enabled=true
                !! 任何人都可以自助注册后台管理员账号，生产环境严禁开启。
                !! 该开关仅用于本地联调；请确认当前不是生产环境，否则立即关闭并重启服务。
                ================================================================================\
                """);
    }
}
