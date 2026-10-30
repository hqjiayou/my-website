package com.mywebsite.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.gateway.GatewayApplication;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway→Auth XFF 信任邊界（Issue #1 的末段約定，Issue #136 收成 TCP 對端）。
 *
 * <p>{@code trusted-proxies} 必須是 {@code (?!)}。匹配到對端時，Netty 會用轉發頭第一段覆蓋連接地址，
 * 直接調 {@link XForwardedHeadersFilter} 並手寫 remoteAddress 看不到這一步。這裡證明
 * {@link ClientIpGatewayFilter} 把出站 {@code X-Forwarded-For} 改成對端；經真實 Netty 的斷言在
 * {@link SpoofedForwardedForGatewayTest}。Auth {@code ClientIpUtils} 取最後一段，由 auth 限流 MVC 覆蓋。
 */
@SpringBootTest(classes = GatewayApplication.class)
class GatewayXForwardedTrustBoundaryTest {

    private static final String XFF = "X-Forwarded-For";
    private static final String SPOOFED = "1.1.1.1";
    private static final String PEER = "203.0.113.7";

    /** 由 application.yml 裝配的 filter 本人（trusted-proxies 未配置時此 bean 不存在，上下文即失敗）。 */
    @Autowired private XForwardedHeadersFilter headerFilter;

    @Autowired private GatewayProperties gatewayProperties;

    private final ClientIpGatewayFilter clientIpFilter = clientIpFilterTrustingNone();

    @Test
    void contextWiresXForwardedFilterWithForAppend() {
        assertThat(headerFilter).isNotNull();
        assertThat(headerFilter.isEnabled()).isTrue();
        assertThat(headerFilter.isForEnabled()).isTrue();
        assertThat(headerFilter.isForAppend()).isTrue();
        assertThat(gatewayProperties.getTrustedProxies()).isEqualTo(ClientIpGatewayFilter.TRUST_NONE);
    }

    @Test
    void spoofedPrefixDoesNotBecomeLastSegment() {
        ServerWebExchange chained = applyClientIp(PEER, SPOOFED, null);

        String forwardedFor = chained.getRequest().getHeaders().getFirst(XFF);
        assertThat(forwardedFor).isNotNull();
        // Auth 側 ClientIpUtils 取最後一段作限流鍵：必須是真實對端，不能是偽造前綴。
        assertThat(lastSegment(forwardedFor)).isEqualTo(PEER);
        assertThat(lastSegment(forwardedFor)).isNotEqualTo(SPOOFED);
        assertThat(chained.getRequest().getHeaders().getFirst("Forwarded")).isNull();
    }

    @Test
    void multipleSpoofedPrefixesStillEndWithRealPeer() {
        ServerWebExchange chained = applyClientIp(PEER, SPOOFED + ", 2.2.2.2", "for=9.9.9.9");

        assertThat(lastSegment(chained.getRequest().getHeaders().getFirst(XFF))).isEqualTo(PEER);
        assertThat(chained.getRequest().getHeaders().getFirst("Forwarded")).isNull();
    }

    @Test
    void withoutInboundXffForwardedForIsPeerOnly() {
        ServerWebExchange chained = applyClientIp(PEER, null, null);

        assertThat(chained.getRequest().getHeaders().getFirst(XFF)).isEqualTo(PEER);
    }

    @Test
    void trustingAnyPeerIsRejectedAtStartup() {
        GatewayProperties properties = new GatewayProperties();
        properties.setTrustedProxies(".*");

        assertThatThrownBy(() -> new ClientIpGatewayFilter(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ClientIpGatewayFilter.TRUST_NONE);
    }

    /**
     * 庫過濾器在「對端已是真實 LB」時會剝掉偽造 XFF。部署不用這條：對端一旦匹配 trusted-proxies，
     * Netty 會先把連接地址改成轉發頭第一段，過濾器看到的就不再是 LB。現行配置是 {@code (?!)}。
     */
    @Test
    void narrowedTrustedProxiesStripSpoofedEntries() {
        XForwardedHeadersFilter prodFilter = new XForwardedHeadersFilter("^10\\.0\\.0\\.\\d+$");

        HttpHeaders out = forward(prodFilter, "10.0.0.9", SPOOFED);

        assertThat(out.getFirst(XFF)).isEqualTo("10.0.0.9");
        assertThat(lastSegment(out.getFirst(XFF))).isNotEqualTo(SPOOFED);
    }

    /**
     * 庫過濾器在對端不匹配時原樣放行偽造 XFF、也不追加真實對端。所以不能靠「收窄正則」防偽造：
     * 不匹配就放行，匹配又會先被 Netty 用第一段覆蓋連接地址。現行配置是 {@code (?!)}，
     * 出站地址由 {@link ClientIpGatewayFilter} 寫成 TCP 對端。
     */
    @Test
    void untrustedPeerGetsNoAppendedTrustedEntry() {
        XForwardedHeadersFilter prodFilter = new XForwardedHeadersFilter("^10\\.0\\.0\\.\\d+$");

        HttpHeaders out = forward(prodFilter, PEER, SPOOFED);

        assertThat(out.getFirst(XFF)).isEqualTo(SPOOFED);
        assertThat(out.getFirst(XFF)).doesNotContain(PEER);
    }

    private static ClientIpGatewayFilter clientIpFilterTrustingNone() {
        GatewayProperties properties = new GatewayProperties();
        properties.setTrustedProxies(ClientIpGatewayFilter.TRUST_NONE);
        return new ClientIpGatewayFilter(properties);
    }

    private ServerWebExchange applyClientIp(String peerIp, String inboundXff, String forwarded) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(
                        HttpMethod.POST, "http://localhost/api/auth/agent/login")
                .remoteAddress(new InetSocketAddress(peerIp, 43210));
        if (inboundXff != null) {
            builder.header(XFF, inboundXff);
        }
        if (forwarded != null) {
            builder.header("Forwarded", forwarded);
        }
        AtomicReference<ServerWebExchange> chained = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            chained.set(exchange);
            return Mono.empty();
        };
        clientIpFilter.filter(MockServerWebExchange.from(builder.build()), chain).block();
        return chained.get();
    }

    private static HttpHeaders forward(XForwardedHeadersFilter filter, String peerIp, String inboundXff) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(
                        HttpMethod.POST, "http://localhost/api/auth/agent/login")
                .remoteAddress(new InetSocketAddress(peerIp, 43210));
        if (inboundXff != null) {
            builder.header(XFF, inboundXff);
        }
        MockServerHttpRequest request = builder.build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        HttpHeaders input = new HttpHeaders();
        input.addAll(request.getHeaders());
        return filter.filter(input, exchange);
    }

    /** 與 Auth 側 {@code ClientIpUtils.resolveClientIp} 相同的「取最後一段」規則（該類在 auth 模塊，此處按契約複刻）。 */
    private static String lastSegment(String xForwardedFor) {
        String[] entries = xForwardedFor.split(",");
        return entries[entries.length - 1].trim();
    }
}
