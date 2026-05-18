package com.mywebsite.gateway.filter;

import com.mywebsite.common.web.HttpRequestCorrelation;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestIdGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestIdGatewayFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String inboundRequestId =
                exchange.getRequest().getHeaders().getFirst(HttpRequestCorrelation.X_REQUEST_ID);
        String requestId =
                inboundRequestId != null && !inboundRequestId.isBlank()
                        ? inboundRequestId
                        : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HttpRequestCorrelation.X_REQUEST_ID, requestId)
                .build();

        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getResponse().getHeaders().set(HttpRequestCorrelation.X_REQUEST_ID, requestId);

        long startedNanos = System.nanoTime();
        return chain.filter(mutated)
                .doFinally(signal -> {
                    long costMs = (System.nanoTime() - startedNanos) / 1_000_000L;
                    Route route = mutated.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String routeLabel = route != null ? route.getId() : "unset";
                    int status =
                            mutated.getResponse().getStatusCode() != null
                                    ? mutated.getResponse().getStatusCode().value()
                                    : 0;
                    log.info(
                            "gateway access requestId={} method={} path={} route={} status={} costMs={}",
                            requestId,
                            mutated.getRequest().getMethod(),
                            mutated.getRequest().getURI().getRawPath(),
                            routeLabel,
                            status,
                            costMs);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
