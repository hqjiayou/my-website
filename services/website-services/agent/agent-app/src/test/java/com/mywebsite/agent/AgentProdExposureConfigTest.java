package com.mywebsite.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * prod 暴露面护栏（Issue #141 UoW-3）：agent 随 Portal 暴露公网，prod profile 下
 * health 不外泄明细且关闭 springdoc（api-docs＋swagger-ui）；基础配置（local 默认）
 * 两者保持开启不影响开发。不启 Spring 上下文。
 */
class AgentProdExposureConfigTest {

    @Test
    void prodProfileHidesHealthDetailsAndDisablesSpringdoc() throws IOException {
        String yaml = readClasspathYaml("/application-prod.yml");

        assertThat(yaml).contains("show-details: never");
        assertThat(yaml).doesNotContain("show-details: always");
        assertThat(yaml).contains("api-docs:");
        assertThat(yaml).contains("swagger-ui:");
        assertThat(yaml).contains("enabled: false");
        assertThat(yaml).doesNotContain("enabled: true");
    }

    @Test
    void baseProfileKeepsHealthDetailsAndSpringdocForLocalDev() throws IOException {
        String yaml = readClasspathYaml("/application.yml");

        assertThat(yaml).contains("show-details: always");
        assertThat(yaml).contains("enabled: true");
    }

    private static String readClasspathYaml(String path) throws IOException {
        try (InputStream in = AgentProdExposureConfigTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path + " 应在 agent-app classpath 上").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
