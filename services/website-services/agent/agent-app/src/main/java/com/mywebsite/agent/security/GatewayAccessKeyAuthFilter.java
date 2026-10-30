package com.mywebsite.agent.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.GatewayV1ErrorDto;
import com.mywebsite.agent.entity.AgentGatewayAccessKeyEntity;
import com.mywebsite.agent.mapper.AgentGatewayAccessKeyMapper;
import com.mywebsite.infra.crypto.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对外 API 网关 {@code /v1/**} 鉴权：{@code Authorization: Bearer mwgw_...}。
 * 不走站点 JWT（见 {@link com.mywebsite.agent.config.GatewayV1SecurityConfig} 的独立安全链），
 * 失败一律 401 + OpenAI 错误形状，不暴露具体是缺失/格式错/查无/吊销/过期中的哪一种。
 */
@Component
public class GatewayAccessKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_PREFIX = "mwgw_";

    private final AgentGatewayAccessKeyMapper accessKeyMapper;
    private final ObjectMapper objectMapper;

    public GatewayAccessKeyAuthFilter(AgentGatewayAccessKeyMapper accessKeyMapper, ObjectMapper objectMapper) {
        this.accessKeyMapper = accessKeyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = bearerToken(request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token) || !token.startsWith(KEY_PREFIX)) {
            writeUnauthorized(response);
            return;
        }
        AgentGatewayAccessKeyEntity entry = accessKeyMapper.selectOne(
                new LambdaQueryWrapper<AgentGatewayAccessKeyEntity>()
                        .eq(AgentGatewayAccessKeyEntity::getKeyHash, ApiKeyHasher.sha256Hex(token))
                        .last("LIMIT 1"));
        if (entry == null || !isUsable(entry)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isUsable(AgentGatewayAccessKeyEntity entry) {
        if (entry.getRevokedAt() != null) {
            return false;
        }
        OffsetDateTime expiresAt = entry.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static String bearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(GatewayV1ErrorDto.of(
                "Incorrect API key provided.", "authentication_error", "invalid_api_key")));
    }
}
