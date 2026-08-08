package com.mywebsite.auth.utils;

import jakarta.servlet.http.HttpServletRequest;

/** 解析客户端 IP（限流维度用）。 */
public final class ClientIpUtils {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpUtils() {}

    /**
     * 取 {@code X-Forwarded-For} 的<strong>最后一个</strong>条目。
     *
     * <p>安全边界：gateway 的 XForwardedHeadersFilter 会把「与网关直连的对端 IP」追加到 XFF 末尾；
     * 而客户端可在请求里伪造 XFF 前缀条目。因此只有最后一个条目可信，取首个条目会让攻击者
     * 通过伪造头随意切换限流桶。无 XFF 时回退 {@link HttpServletRequest#getRemoteAddr()}（直连场景）。
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
