package com.mywebsite.gateway.filter;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 限流用的客户端 IP 只取 TCP 对端。
 *
 * <p>Spring Cloud Gateway 在对端匹配 {@code trusted-proxies} 时，会用 {@code X-Forwarded-For} 或
 * {@code Forwarded} 的第一段覆盖连接地址。第一段可由客户端伪造。因此 {@code trusted-proxies} 必须是
 * {@link #TRUST_NONE}（谁都不匹配），这里再把出站 {@code X-Forwarded-For} 写成 Netty 看到的对端。
 */
@Component
public class ClientIpGatewayFilter implements GlobalFilter, Ordered {

    static final String TRUST_NONE = "(?!)";

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private static final String FORWARDED = "Forwarded";

    public ClientIpGatewayFilter(GatewayProperties properties) {
        String trusted = properties.getTrustedProxies();
        if (!TRUST_NONE.equals(trusted)) {
            throw new IllegalStateException(
                    "gateway trusted-proxies must be " + TRUST_NONE
                            + " so the TCP peer is not replaced by a forwarded header; actual="
                            + trusted);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return chain.filter(exchange);
        }
        String peer = remote.getAddress().getHostAddress();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(X_FORWARDED_FOR, peer);
                    headers.remove(FORWARDED);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
