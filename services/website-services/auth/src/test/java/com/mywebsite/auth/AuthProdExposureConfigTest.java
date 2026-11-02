package com.mywebsite.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * prod 暴露面护栏（Issue #141 UoW-3）：prod profile 下 health 不外泄明细；
 * 基础配置（local 默认）保持 show-details: always 不影响开发。不启 Spring 上下文。
 */
class AuthProdExposureConfigTest {

    @Test
    void prodProfileHidesHealthDetails() throws IOException {
        String yaml = readClasspathYaml("/application-prod.yml");

        assertThat(yaml).contains("show-details: never");
        assertThat(yaml).doesNotContain("show-details: always");
    }

    @Test
    void baseProfileKeepsHealthDetailsForLocalDev() throws IOException {
        // auth 的 src/test/resources 自带 application.yml（测试 JWT 密钥），classpath 上会遮蔽
        // main 的同名文件，故基础配置改读 main 源文件（surefire 工作目录为模块根）。
        String yaml = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).contains("show-details: always");
    }

    private static String readClasspathYaml(String path) throws IOException {
        try (InputStream in = AuthProdExposureConfigTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path + " 应在 auth classpath 上").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
