package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin 登录 / 注册等策略配置（前缀 {@code security.auth}）。 */
@ConfigurationProperties(prefix = "security.auth")
public class AdminAuthProperties {

    private int adminLoginMaxPasswordLength = 128;

    /** 注册密码下限（字符数与登录口令同一统计方式）。 */
    private int adminRegisterMinPasswordLength = 8;

    /**
     * 当系统中尚无任何<strong>活跃</strong>管理员时，允许通过本接口创建第一个账号。
     *
     * <p>建议在具备运营侧邀请能力后在生产禁用或仅首轮部署启用。
     */
    private boolean adminBootstrapRegistrationEnabled = true;

    /**
     * 是否允许在非 bootstrap 状态下继续自助注册（默认关闭；仅建议本地联调启用）。
     */
    private boolean adminOpenRegistrationEnabled = false;

    public int getAdminLoginMaxPasswordLength() {
        return adminLoginMaxPasswordLength;
    }

    public void setAdminLoginMaxPasswordLength(int adminLoginMaxPasswordLength) {
        this.adminLoginMaxPasswordLength = adminLoginMaxPasswordLength;
    }

    public int getAdminRegisterMinPasswordLength() {
        return adminRegisterMinPasswordLength;
    }

    public void setAdminRegisterMinPasswordLength(int adminRegisterMinPasswordLength) {
        this.adminRegisterMinPasswordLength = adminRegisterMinPasswordLength;
    }

    public boolean isAdminBootstrapRegistrationEnabled() {
        return adminBootstrapRegistrationEnabled;
    }

    public void setAdminBootstrapRegistrationEnabled(boolean adminBootstrapRegistrationEnabled) {
        this.adminBootstrapRegistrationEnabled = adminBootstrapRegistrationEnabled;
    }

    public boolean isAdminOpenRegistrationEnabled() {
        return adminOpenRegistrationEnabled;
    }

    public void setAdminOpenRegistrationEnabled(boolean adminOpenRegistrationEnabled) {
        this.adminOpenRegistrationEnabled = adminOpenRegistrationEnabled;
    }
}
