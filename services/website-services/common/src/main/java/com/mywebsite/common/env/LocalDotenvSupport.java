package com.mywebsite.common.env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spring Boot 不读取同级 {@code .env}；仅在本地 startup 注入为系统属性，供 {@code ${DB_*}} 等占位绑定。
 * 不覆盖已有的 OS 环境变量或既有 {@link System#setProperty}。
 */
public final class LocalDotenvSupport {

    private LocalDotenvSupport() {}

    /** 从 {@code user.dir} 起向祖先目录查找最近一个 {@code .env}（最多 12 层）。解析失败或非文件则静默跳过。 */
    public static void tryLoadNearestEnv() {
        try {
            Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            for (int depth = 0; depth < 12 && dir != null; depth++) {
                Path envFile = dir.resolve(".env");
                if (Files.isRegularFile(envFile)) {
                    applyEnvFile(envFile);
                    return;
                }
                dir = dir.getParent();
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
            // best-effort：启动前无日志基础设施
        }
    }

    static void applyEnvFile(Path envFile) throws IOException {
        for (String raw : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq >= line.length() - 1) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) {
                continue;
            }
            if (System.getenv(key) != null || System.getProperty(key) != null) {
                continue;
            }
            String value = unquote(line.substring(eq + 1).trim());
            System.setProperty(key, value);
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
