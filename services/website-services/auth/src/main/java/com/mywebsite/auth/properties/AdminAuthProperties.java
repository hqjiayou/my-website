package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin 登录校验等非 Cookie/TTL 配置（前缀 {@code security.auth}）。 */
@ConfigurationProperties(prefix = "security.auth")
public class AdminAuthProperties {

    private int adminLoginMaxPasswordLength = 128;

    public int getAdminLoginMaxPasswordLength() {
        return adminLoginMaxPasswordLength;
    }

    public void setAdminLoginMaxPasswordLength(int adminLoginMaxPasswordLength) {
        this.adminLoginMaxPasswordLength = adminLoginMaxPasswordLength;
    }
}
