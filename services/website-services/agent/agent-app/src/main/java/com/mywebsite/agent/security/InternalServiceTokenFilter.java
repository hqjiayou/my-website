package com.mywebsite.agent.security;

import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.agent.properties.AgentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /internal/** 内部服务鉴权（Phase 4：agent-services 业务工具回调）。
 *
 * <p>与 agent-services 共享同一个 internal token（双向同一信任域）。
 * token 未配置时 internal API 整体不可用（403），不会静默放行。
 */
@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private final AgentProperties agentProperties;

    public InternalServiceTokenFilter(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String expected = agentProperties.getInternalServiceToken();
        String provided = request.getHeader(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN);
        if (!StringUtils.hasText(expected) || !constantTimeEquals(expected, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":1002,\"message\":\"unauthorized\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
