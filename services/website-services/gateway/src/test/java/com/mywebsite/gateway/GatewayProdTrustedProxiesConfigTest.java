package com.mywebsite.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * prod profile 的 trusted-proxies 護欄（Issue #1）：application-prod.yml 必須以
 * 無默認值的環境變量佔位符提供 trusted-proxies（未配置即啟動失敗），且不得含 dev 的 ".*"。
 * 不啟 Spring 上下文——prod 上下文在本機/CI 本就應因缺環境變量而 fail-fast。
 */
class GatewayProdTrustedProxiesConfigTest {

    @Test
    void prodProfileRequiresEnvProvidedTrustedProxies() throws IOException {
        String yaml;
        try (InputStream in = getClass().getResourceAsStream("/application-prod.yml")) {
            assertThat(in).as("application-prod.yml 應在 gateway classpath 上").isNotNull();
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(yaml).contains("${SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES}");
        // 配置行不得把 trusted-proxies 設成 dev 的 ".*"（註釋裡提及 ".*" 不算）
        assertThat(yaml).doesNotContain("trusted-proxies: \".*\"");
        assertThat(yaml).contains("trusted-proxies: \"${SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES}\"");
    }
}
