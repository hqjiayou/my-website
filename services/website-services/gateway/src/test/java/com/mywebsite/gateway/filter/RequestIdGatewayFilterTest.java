package com.mywebsite.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.common.web.HttpRequestCorrelation;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestIdGatewayFilterTest {

    private final RequestIdGatewayFilter filter = new RequestIdGatewayFilter();

    @Test
    void propagatesInboundRequestIdOnRequestAndResponse() {
        MockServerHttpRequest req = MockServerHttpRequest.method(HttpMethod.GET, "http://localhost/api/auth/ping")
                .header(HttpRequestCorrelation.X_REQUEST_ID, "rid-stable")
                .build();
        ServerWebExchange incoming = MockServerWebExchange.from(req);

        AtomicReference<ServerWebExchange> chained = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            chained.set(exchange);
            // 必须把 setComplete() 的 Mono 作为链的返回值被订阅，提交动作
            // （含 beforeCommit 回调）才会真正执行；旧写法只是调用未订阅，
            // 靠 doFinally 直接写可写 Mock 头才偶然通过。
            return exchange.getResponse().setComplete();
        };

        StepVerifier.create(filter.filter(incoming, chain)).verifyComplete();

        assertThat(chained.get().getRequest().getHeaders().getFirst(HttpRequestCorrelation.X_REQUEST_ID))
                .isEqualTo("rid-stable");
        assertThat(chained.get().getResponse().getHeaders().getFirst(HttpRequestCorrelation.X_REQUEST_ID))
                .isEqualTo("rid-stable");
    }

    @Test
    void generatesUuidWhenInboundHeaderAbsent() {
        MockServerHttpRequest req =
                MockServerHttpRequest.method(HttpMethod.GET, "http://localhost/api/site/ping").build();
        ServerWebExchange incoming = MockServerWebExchange.from(req);

        AtomicReference<ServerWebExchange> chained = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            chained.set(exchange);
            // 必须把 setComplete() 的 Mono 作为链的返回值被订阅，提交动作
            // （含 beforeCommit 回调）才会真正执行；旧写法只是调用未订阅，
            // 靠 doFinally 直接写可写 Mock 头才偶然通过。
            return exchange.getResponse().setComplete();
        };

        StepVerifier.create(filter.filter(incoming, chain)).verifyComplete();

        String propagated = chained.get().getRequest().getHeaders().getFirst(HttpRequestCorrelation.X_REQUEST_ID);
        assertThat(propagated).isNotBlank();
        assertThat(chained.get().getResponse().getHeaders().getFirst(HttpRequestCorrelation.X_REQUEST_ID))
                .isEqualTo(propagated);
    }
}
