package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HttpAgentClientTest {

    private HttpServer server;
    private AgentProperties properties;
    private HttpAgentClient client;
    private final AtomicInteger chatHits = new AtomicInteger();
    private final AtomicInteger healthHits = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/health", exchange -> {
            healthHits.incrementAndGet();
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            if (!"secret-token".equals(token)) {
                write(exchange, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
                return;
            }
            write(
                    exchange,
                    200,
                    "{\"ok\":true,\"service\":\"agent-service\",\"providerStatus\":\"BLOCKED\"}");
        });
        server.createContext("/internal/v1/chat", exchange -> {
            chatHits.incrementAndGet();
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            if (!"secret-token".equals(token)) {
                write(exchange, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
                return;
            }
            write(
                    exchange,
                    503,
                    "{\"ok\":false,\"errorCode\":\"EXTERNAL_DEPENDENCY_BLOCKED\",\"providerStatus\":\"BLOCKED\",\"message\":\"no key\"}");
        });
        server.start();

        properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setInternalServiceToken("secret-token");
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(2000);
        properties.setRetryMaxAttempts(1);

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        client = new HttpAgentClient(properties, new ObjectMapper(), restClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pingMapsHealthResponse() {
        AgentPingResponse ping = client.ping();
        assertThat(ping.message()).contains("agent-service");
        assertThat(ping.providerStatus()).isEqualTo("BLOCKED");
        assertThat(healthHits.get()).isEqualTo(1);
    }

    @Test
    void chatMapsExternalDependencyBlockedWithoutRetryFlood() {
        assertThatThrownBy(() -> client.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
        // blocked 不重试
        assertThat(chatHits.get()).isEqualTo(1);
    }

    @Test
    void missingTokenThrowsBlockedBeforeHttp() {
        properties.setInternalServiceToken(" ");
        assertThatThrownBy(() -> client.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
        assertThat(chatHits.get()).isZero();
    }

    @Test
    void invalidInternalTokenMapsToSystemError() throws IOException {
        properties.setInternalServiceToken("wrong");
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        HttpAgentClient wrongTokenClient = new HttpAgentClient(properties, new ObjectMapper(), restClient);

        assertThatThrownBy(() -> wrongTokenClient.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }

    @Test
    void liveSuccessIsMapped() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"conversationId\":null,\"providerStatus\":\"LIVE\"}}"));

        AgentChatResponse response = client.chat(new AgentChatRequest("10", "hello"));
        assertThat(response.reply()).isEqualTo("hi");
        assertThat(response.providerStatus()).isEqualTo("LIVE");
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
