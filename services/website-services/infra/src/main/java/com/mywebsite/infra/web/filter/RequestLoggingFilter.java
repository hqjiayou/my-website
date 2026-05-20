package com.mywebsite.infra.web.filter;

import com.mywebsite.common.web.HttpRequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 应用统一：透传/生成 {@link HttpRequestCorrelation#X_REQUEST_ID}、MDC 注入、INFO 访问日志（不含敏感头/体）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final Environment environment;

    public RequestLoggingFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HttpRequestCorrelation.X_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        // 入站已带 X-Request-Id（通常经 Gateway 转发）时不在 Servlet 回写，由网关在响应完成时写一次。
        boolean inboundCorrelation =
                request.getHeader(HttpRequestCorrelation.X_REQUEST_ID) != null
                        && !request.getHeader(HttpRequestCorrelation.X_REQUEST_ID).isBlank();
        if (!inboundCorrelation) {
            response.setHeader(HttpRequestCorrelation.X_REQUEST_ID, requestId);
        }
        MDC.put(HttpRequestCorrelation.MDC_REQUEST_ID, requestId);

        String serviceName =
                Optional.ofNullable(environment.getProperty("spring.application.name")).orElse("servlet-app");
        long startMs = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - startMs;
            int status = response.getStatus();
            String path = request.getRequestURI();
            log.info(
                    "{} access requestId={} method={} path={} status={} costMs={}",
                    serviceName,
                    requestId,
                    request.getMethod(),
                    path,
                    status,
                    costMs);
            MDC.remove(HttpRequestCorrelation.MDC_REQUEST_ID);
        }
    }
}
