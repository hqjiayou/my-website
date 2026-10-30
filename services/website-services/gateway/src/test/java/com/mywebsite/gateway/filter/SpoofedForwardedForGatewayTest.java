package com.mywebsite.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.gateway.GatewayApplication;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 经真实 Netty 入口的 XFF 信任边界（Issue #136）。
 *
 * <p>{@code GatewayXForwardedTrustBoundaryTest} 直接调用 {@code XForwardedHeadersFilter} 并手写
 * remoteAddress，看不到 {@code trusted-proxies} 匹配时 Netty 用转发头第一段覆盖 TCP 对端。
 * 本测试打到已启动的 gateway，下游看到的 {@code X-Forwarded-For} 末段必须是测试客户端的对端，
 * 不能是伪造值。auth {@code ClientIpUtils} 取的就是这一段。
 */
@SpringBootTest(classes = GatewayApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class SpoofedForwardedForGatewayTest {

    private static final AtomicReference<String> forwardedFor = new AtomicReference<>();
    private static HttpServer downstream;

    @DynamicPropertySource
    static void downstreamRoute(DynamicPropertyRegistry registry) {
        try {
            downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        downstream.createContext("/api/auth/ping", exchange -> {
            forwardedFor.set(exchange.getRequestHeaders().getFirst("X-Forwarded-For"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        downstream.start();
        int port = downstream.getAddress().getPort();
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "auth-service-api");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://127.0.0.1:" + port);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/auth/**");
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void spoofedXForwardedForDoesNotBecomeDownstreamClientIp() {
        assertDownstreamPeer("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
    }

    @Test
    void spoofedForwardedForDoesNotBecomeDownstreamClientIp() {
        assertDownstreamPeer("Forwarded", "for=1.1.1.1");
    }

    private void assertDownstreamPeer(String headerName, String headerValue) {
        forwardedFor.set(null);
        webTestClient
                .mutate()
                .responseTimeout(Duration.ofSeconds(10))
                .build()
                .get()
                .uri("/api/auth/ping")
                .header(headerName, headerValue)
                .exchange()
                .expectStatus()
                .isOk();

        String xff = forwardedFor.get();
        assertThat(xff).isNotBlank();
        String last = xff.substring(xff.lastIndexOf(',') + 1).trim();
        assertThat(last).isNotEqualTo("1.1.1.1");
        assertThat(last).isIn("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    }
}
