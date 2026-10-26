package com.mywebsite.admin.testsupport;

import com.mywebsite.admin.AdminApplication;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 为 Admin 集成测试提供本进程内 JWKS HTTP 端点，避免依赖真实 Auth 进程，且满足 {@code mvn test} 离线可跑。
 */
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
public abstract class AbstractAdminSpringBootTest {

    private static HttpServer jwksHttpServer;

    @DynamicPropertySource
    static void registerTestJwksUri(DynamicPropertyRegistry registry) throws Exception {
        if (jwksHttpServer == null) {
            byte[] body = AdminJwksTestFixture.jwksJson().getBytes(StandardCharsets.UTF_8);
            jwksHttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksHttpServer.createContext(
                    "/.well-known/jwks.json",
                    (HttpExchange ex) -> {
                        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                        ex.sendResponseHeaders(200, body.length);
                        try (OutputStream os = ex.getResponseBody()) {
                            os.write(body);
                        }
                    });
            jwksHttpServer.start();
        }
        int port = jwksHttpServer.getAddress().getPort();
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + port + "/.well-known/jwks.json");
        registry.add("mywebsite.ai.model-entry-secret", () -> "test-model-entry-secret");
    }
}
