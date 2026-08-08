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

    /** 认证限流总开关（逃生阀）；关闭后登录 / 注册 / 刷新均不做限流检查与计数。 */
    private boolean adminRateLimitEnabled = true;

    /** 登录 IP+username 维度：失败次数阈值。 */
    private int adminLoginIpUserFailureThreshold = 5;

    /** 登录 IP+username 维度：窗口 / cooldown 秒数。 */
    private int adminLoginIpUserWindowSeconds = 300;

    /** 登录 username（跨 IP）维度：失败次数阈值。 */
    private int adminLoginUserFailureThreshold = 10;

    /** 登录 username（跨 IP）维度：窗口 / cooldown 秒数。 */
    private int adminLoginUserWindowSeconds = 900;

    /** 登录 IP（跨 username）维度：失败次数阈值。 */
    private int adminLoginIpFailureThreshold = 20;

    /** 登录 IP（跨 username）维度：窗口 / cooldown 秒数。 */
    private int adminLoginIpWindowSeconds = 900;

    /** 注册 IP 维度：次数阈值（成功失败都计）。 */
    private int adminRegisterIpThreshold = 5;

    /** 注册 IP 维度：窗口 / cooldown 秒数。 */
    private int adminRegisterIpWindowSeconds = 3600;

    /** 刷新 IP 维度：失败次数阈值（成功刷新不计数）。 */
    private int adminRefreshIpFailureThreshold = 30;

    /** 刷新 IP 维度：窗口 / cooldown 秒数。 */
    private int adminRefreshIpWindowSeconds = 600;

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

    public boolean isAdminRateLimitEnabled() {
        return adminRateLimitEnabled;
    }

    public void setAdminRateLimitEnabled(boolean adminRateLimitEnabled) {
        this.adminRateLimitEnabled = adminRateLimitEnabled;
    }

    public int getAdminLoginIpUserFailureThreshold() {
        return adminLoginIpUserFailureThreshold;
    }

    public void setAdminLoginIpUserFailureThreshold(int adminLoginIpUserFailureThreshold) {
        this.adminLoginIpUserFailureThreshold = adminLoginIpUserFailureThreshold;
    }

    public int getAdminLoginIpUserWindowSeconds() {
        return adminLoginIpUserWindowSeconds;
    }

    public void setAdminLoginIpUserWindowSeconds(int adminLoginIpUserWindowSeconds) {
        this.adminLoginIpUserWindowSeconds = adminLoginIpUserWindowSeconds;
    }

    public int getAdminLoginUserFailureThreshold() {
        return adminLoginUserFailureThreshold;
    }

    public void setAdminLoginUserFailureThreshold(int adminLoginUserFailureThreshold) {
        this.adminLoginUserFailureThreshold = adminLoginUserFailureThreshold;
    }

    public int getAdminLoginUserWindowSeconds() {
        return adminLoginUserWindowSeconds;
    }

    public void setAdminLoginUserWindowSeconds(int adminLoginUserWindowSeconds) {
        this.adminLoginUserWindowSeconds = adminLoginUserWindowSeconds;
    }

    public int getAdminLoginIpFailureThreshold() {
        return adminLoginIpFailureThreshold;
    }

    public void setAdminLoginIpFailureThreshold(int adminLoginIpFailureThreshold) {
        this.adminLoginIpFailureThreshold = adminLoginIpFailureThreshold;
    }

    public int getAdminLoginIpWindowSeconds() {
        return adminLoginIpWindowSeconds;
    }

    public void setAdminLoginIpWindowSeconds(int adminLoginIpWindowSeconds) {
        this.adminLoginIpWindowSeconds = adminLoginIpWindowSeconds;
    }

    public int getAdminRegisterIpThreshold() {
        return adminRegisterIpThreshold;
    }

    public void setAdminRegisterIpThreshold(int adminRegisterIpThreshold) {
        this.adminRegisterIpThreshold = adminRegisterIpThreshold;
    }

    public int getAdminRegisterIpWindowSeconds() {
        return adminRegisterIpWindowSeconds;
    }

    public void setAdminRegisterIpWindowSeconds(int adminRegisterIpWindowSeconds) {
        this.adminRegisterIpWindowSeconds = adminRegisterIpWindowSeconds;
    }

    public int getAdminRefreshIpFailureThreshold() {
        return adminRefreshIpFailureThreshold;
    }

    public void setAdminRefreshIpFailureThreshold(int adminRefreshIpFailureThreshold) {
        this.adminRefreshIpFailureThreshold = adminRefreshIpFailureThreshold;
    }

    public int getAdminRefreshIpWindowSeconds() {
        return adminRefreshIpWindowSeconds;
    }

    public void setAdminRefreshIpWindowSeconds(int adminRefreshIpWindowSeconds) {
        this.adminRefreshIpWindowSeconds = adminRefreshIpWindowSeconds;
    }
}
