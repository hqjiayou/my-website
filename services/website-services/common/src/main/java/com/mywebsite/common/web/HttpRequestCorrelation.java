package com.mywebsite.common.web;

/**
 * 跨 HTTP 网关与 Servlet 应用共享的稳定 Header / MDC 键名（避免魔法字符串分叉）。
 */
public final class HttpRequestCorrelation {

    /** 链路追踪：请求头与响应头统一键名（与常见网关约定对齐）。 */
    public static final String X_REQUEST_ID = "X-Request-Id";

    /** SLF4J {@link org.slf4j.MDC} 中存放链路 ID 的键。 */
    public static final String MDC_REQUEST_ID = "requestId";

    private HttpRequestCorrelation() {}
}
