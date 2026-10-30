package com.mywebsite.auth.utils;

import jakarta.servlet.http.HttpServletRequest;

/** 解析客户端 IP（限流维度用）。 */
public final class ClientIpUtils {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpUtils() {}

    /**
     * 取 {@code X-Forwarded-For} 的<strong>最后一个</strong>条目。
     *
     * <p>安全边界：gateway 的 {@code ClientIpGatewayFilter} 把本头改写成与网关直连的 TCP 对端
     * （并去掉客户端带来的 {@code Forwarded}）。因此只有最后一段可信；取首段会让攻击者用伪造前缀换限流桶。
     * 无本头时回退 {@link HttpServletRequest#getRemoteAddr()}（直连场景）。
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            String[] entries = xff.split(",");
            String last = entries[entries.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
