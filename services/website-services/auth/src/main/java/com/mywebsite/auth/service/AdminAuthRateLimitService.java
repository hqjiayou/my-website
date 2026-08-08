package com.mywebsite.auth.service;

/**
 * Admin 认证限流（登录 / 注册 / 刷新）。
 *
 * <p>总原则：登录与刷新只计失败；计数 key 即 cooldown key（INCR + 首次创建时 EXPIRE）。
 * 计数达到阈值后拒绝，且每次超限尝试将 TTL 重置回窗口时长——攻击停止后窗口自然过期即自动恢复，无永久锁。
 *
 * <p>{@code usernameNorm} 为限流专用归一化用户名（{@code trim().toLowerCase(Locale.ROOT)}），
 * 仅用于限流 key，不影响登录本身的用户名匹配语义。
 */
public interface AdminAuthRateLimitService {

    /** 登录三维度（IP+username / username 跨 IP / IP 跨 username）任一超限即 false；超限维度 TTL 重置回窗口。 */
    boolean loginAllowed(String clientIp, String usernameNorm);

    /** 登录失败（含未知用户名的 dummy hash 路径）时三维度各 +1。 */
    void recordLoginFailure(String clientIp, String usernameNorm);

    /** 登录成功时删除 IP+username 与 username 两个维度的计数；IP 维度不重置。 */
    void resetLoginFailures(String clientIp, String usernameNorm);

    /** 注册按 IP 计数（成功失败都计，本方法自身完成记录）；超过阈值即 false 并重置 TTL。 */
    boolean registerAllowed(String clientIp);

    /** 刷新按 IP 检查失败计数是否达阈值；超限时 TTL 重置回窗口。 */
    boolean refreshAllowed(String clientIp);

    /** 刷新失败（cookie 缺失 / Invalid / ReplayRevoked）时 IP 维度 +1；成功刷新不计数也不重置。 */
    void recordRefreshFailure(String clientIp);
}
