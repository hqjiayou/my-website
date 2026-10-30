package com.mywebsite.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * trusted-proxies 護欄（Issue #136）：dev 與 prod 都必須是 {@code (?!)}。
 * 匹配到對端時 Netty 會用轉發頭第一段覆蓋 TCP 對端，環境變量收窄成 LB 正則也會重新打開這條路徑。
 * 不啟 Spring 上下文。
 */
class GatewayProdTrustedProxiesConfigTest {

    @Test
    void prodProfileTrustsNoPeer() throws IOException {
        String yaml = readClasspathYaml("/application-prod.yml");

        assertThat(yaml).contains("trusted-proxies: \"(?!)\"");
        assertThat(yaml).doesNotContain("trusted-proxies: \".*\"");
        assertThat(yaml).doesNotContain("${SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES}");
    }

    @Test
    void defaultProfileTrustsNoPeer() throws IOException {
        String yaml = readClasspathYaml("/application.yml");

        assertThat(yaml).contains("trusted-proxies: \"(?!)\"");
        assertThat(yaml).doesNotContain("trusted-proxies: \".*\"");
        assertThat(yaml).contains("forward-headers-strategy: none");
    }

    private static String readClasspathYaml(String path) throws IOException {
        try (InputStream in = GatewayProdTrustedProxiesConfigTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path + " 應在 gateway classpath 上").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
