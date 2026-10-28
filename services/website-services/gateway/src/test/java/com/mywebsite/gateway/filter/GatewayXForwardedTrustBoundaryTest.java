package com.mywebsite.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.gateway.GatewayApplication;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

/**
 * Gateway→Auth XFF 信任邊界斷言（Issue #1）。
 *
 * <p>證據鏈：本測試證明經 gateway 的 {@link XForwardedHeadersFilter}（按 {@code application.yml}
 * 現行配置：{@code trusted-proxies: ".*"} + {@code for-append: true}，由 Spring 上下文實際裝配的
 * bean）轉發後，下游收到的 {@code X-Forwarded-For} <strong>最後一段</strong>是與 gateway 直連的
 * 真實對端 IP，而不是客戶端偽造的前綴；Auth 側 {@code ClientIpUtils} 恰好取最後一段作限流
 * bucket，其行為由 auth 模塊的限流 MVC 測試覆蓋。兩側合起來即 Gateway→Auth 的證明。
 */
@SpringBootTest(classes = GatewayApplication.class)
class GatewayXForwardedTrustBoundaryTest {

    private static final String XFF = "X-Forwarded-For";
    private static final String SPOOFED = "1.1.1.1";
    private static final String PEER = "203.0.113.7";

    /** 由 application.yml 裝配的 filter 本人（trusted-proxies 未配置時此 bean 不存在，上下文即失敗）。 */
    @Autowired private XForwardedHeadersFilter filter;

    @Test
    void contextWiresXForwardedFilterWithForAppend() {
        assertThat(filter).isNotNull();
        assertThat(filter.isEnabled()).isTrue();
        assertThat(filter.isForEnabled()).isTrue();
        assertThat(filter.isForAppend()).isTrue();
    }

    @Test
    void spoofedPrefixDoesNotBecomeLastSegment() {
        HttpHeaders out = forward(filter, PEER, SPOOFED);

        String forwardedFor = out.getFirst(XFF);
        assertThat(forwardedFor).isNotNull();
        // Auth 側 ClientIpUtils 取最後一段作限流鍵：必須是真實對端，不能是偽造前綴。
        assertThat(lastSegment(forwardedFor)).isEqualTo(PEER);
        assertThat(lastSegment(forwardedFor)).isNotEqualTo(SPOOFED);
    }

    @Test
    void multipleSpoofedPrefixesStillEndWithRealPeer() {
        HttpHeaders out = forward(filter, PEER, SPOOFED + ", 2.2.2.2");

        assertThat(lastSegment(out.getFirst(XFF))).isEqualTo(PEER);
    }

    @Test
    void withoutInboundXffForwardedForIsPeerOnly() {
        HttpHeaders out = forward(filter, PEER, null);

        assertThat(out.getFirst(XFF)).isEqualTo(PEER);
    }

    /**
     * prod 收窄後的行為（regex 只信任前置 LB 10.0.0.0/24）：LB 轉發時，客戶端偽造的
     * XFF 條目因不匹配 trusted-proxies 而被<strong>整段剝除</strong>，下游只剩 LB 對端 IP。
     */
    @Test
    void narrowedTrustedProxiesStripSpoofedEntries() {
        XForwardedHeadersFilter prodFilter = new XForwardedHeadersFilter("^10\\.0\\.0\\.\\d+$");

        HttpHeaders out = forward(prodFilter, "10.0.0.9", SPOOFED);

        assertThat(out.getFirst(XFF)).isEqualTo("10.0.0.9");
        assertThat(lastSegment(out.getFirst(XFF))).isNotEqualTo(SPOOFED);
    }

    /**
     * 語義記錄：對端不被信任時 filter <strong>不追加</strong>任何可信條目、原樣放行——
     * 因此 prod 收窄 trusted-proxies 必須同時配網絡層限制：gateway 只能被前置 LB 到達，
     * 不能對不可信來源直連暴露（見 docs/operations/SECURITY.md、DEPLOYMENT.md）。
     */
    @Test
    void untrustedPeerGetsNoAppendedTrustedEntry() {
        XForwardedHeadersFilter prodFilter = new XForwardedHeadersFilter("^10\\.0\\.0\\.\\d+$");

        HttpHeaders out = forward(prodFilter, PEER, SPOOFED);

        assertThat(out.getFirst(XFF)).isEqualTo(SPOOFED);
        assertThat(out.getFirst(XFF)).doesNotContain(PEER);
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
