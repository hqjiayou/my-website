package com.mywebsite.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * prod 暴露面护栏（Issue #141 UoW-3）：prod profile 下 health 不外泄明细；
 * 基础配置保持 show-details: always 不影响本地诊断。不启 Spring 上下文，
 * 与 GatewayProdTrustedProxiesConfigTest 同法。
 */
class GatewayProdExposureConfigTest {

    @Test
    void prodProfileHidesHealthDetails() throws IOException {
        String yaml = readClasspathYaml("/application-prod.yml");

        assertThat(yaml).contains("show-details: never");
        assertThat(yaml).doesNotContain("show-details: always");
    }

    @Test
    void baseProfileKeepsHealthDetailsForLocalDev() throws IOException {
        String yaml = readClasspathYaml("/application.yml");

        assertThat(yaml).contains("show-details: always");
    }

    private static String readClasspathYaml(String path) throws IOException {
        try (InputStream in = GatewayProdExposureConfigTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path + " 应在 gateway classpath 上").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
