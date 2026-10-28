package com.mywebsite.agent.testsupport;

import com.mywebsite.agent.AgentApplication;
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
 * 为 Agent 集成测试提供本进程内 JWKS 与 agent-services stub，避免依赖外部进程。
 */
@SpringBootTest(classes = AgentApplication.class)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
    FlywayAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
})
public abstract class AbstractAgentSpringBootTest {

    private static HttpServer jwksHttpServer;
    private static HttpServer agentHttpServer;

    public static final String TEST_AGENT_INTERNAL_TOKEN = "test-agent-internal-token";

    @DynamicPropertySource
    static void registerTestInfrastructure(DynamicPropertyRegistry registry) throws Exception {
        if (jwksHttpServer == null) {
            byte[] body = AgentJwksTestFixture.jwksJson().getBytes(StandardCharsets.UTF_8);
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
        if (agentHttpServer == null) {
            agentHttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            agentHttpServer.createContext("/internal/v1/health", AbstractAgentSpringBootTest::handleAgentHealth);
            agentHttpServer.createContext("/internal/v1/chat", AbstractAgentSpringBootTest::handleAgentChatBlocked);
            agentHttpServer.start();
        }

        int jwksPort = jwksHttpServer.getAddress().getPort();
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + jwksPort + "/.well-known/jwks.json");

        int agentPort = agentHttpServer.getAddress().getPort();
        registry.add("mywebsite.agent.base-url", () -> "http://127.0.0.1:" + agentPort);
        registry.add("mywebsite.agent.internal-service-token", () -> TEST_AGENT_INTERNAL_TOKEN);
        registry.add("mywebsite.agent.connect-timeout-ms", () -> "1000");
        registry.add("mywebsite.agent.read-timeout-ms", () -> "2000");
        registry.add("mywebsite.agent.retry-max-attempts", () -> "0");
        registry.add("mywebsite.agent.ai.rate-limit-enabled", () -> "true");
        registry.add("mywebsite.agent.ai.rate-limit-per-minute", () -> "100");
    }

    private static void handleAgentHealth(HttpExchange ex) throws java.io.IOException {
        if (!authorized(ex)) {
            writeJson(ex, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
            return;
        }
        writeJson(
                ex,
                200,
                "{\"ok\":true,\"service\":\"agent-service\",\"providerStatus\":\"BLOCKED\"}");
    }

    private static void handleAgentChatBlocked(HttpExchange ex) throws java.io.IOException {
        if (!authorized(ex)) {
            writeJson(ex, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
            return;
        }
        writeJson(
                ex,
                503,
                "{\"ok\":false,\"errorCode\":\"EXTERNAL_DEPENDENCY_BLOCKED\",\"providerStatus\":\"BLOCKED\",\"message\":\"no key\"}");
    }

    private static boolean authorized(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Internal-Service-Token");
        return TEST_AGENT_INTERNAL_TOKEN.equals(token);
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
