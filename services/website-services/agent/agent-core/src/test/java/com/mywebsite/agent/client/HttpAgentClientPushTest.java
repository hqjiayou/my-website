package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentRuntimeConfigPayload;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.mywebsite.agent.properties.AgentProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * 运行配置推送的 socket-free 契约测试（Issue #146 UoW-1）：以内存 stub 工厂喂 canned
 * 响应，验证请求形状（路径/方法/token 头/载荷 JSON）与 APPLIED/BLOCKED/FAILED 三结果解析。
 * HttpAgentClientTest 的 HttpServer 版本覆盖同一逻辑的真实 HTTP 路径（CI 环境）。
 */
class HttpAgentClientPushTest {

    private static final AgentRuntimeConfigPayload PAYLOAD = new AgentRuntimeConfigPayload(
            "postgresql://user:pw@db.internal:5432/retrieval", "google:gemini-embedding-001", 768, "gemini-key-1", 7L);

    private final StubFactory factory = new StubFactory();
    private final HttpAgentClient client = newClient(factory);

    private static HttpAgentClient newClient(StubFactory factory) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:3001");
        properties.setInternalServiceToken("secret-token");
        ObjectMapper mapper = new ObjectMapper();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:3001")
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
                .build();
        return new HttpAgentClient(properties, mapper, restClient);
    }

    @Test
    void appliedParsesVersionAndSendsExpectedRequest() {
        factory.enqueue(HttpStatus.OK, "{\"applied\":true,\"appliedVersion\":7}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(PAYLOAD);

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_APPLIED);
        assertThat(result.appliedVersion()).isEqualTo(7L);
        assertThat(factory.lastMethod).isEqualTo(HttpMethod.PUT);
        assertThat(factory.lastUri.getPath()).isEqualTo("/internal/v1/config/retrieval");
        assertThat(factory.lastHeaders.getFirst("X-Internal-Service-Token")).isEqualTo("secret-token");
        assertThat(factory.lastBody)
                .contains("\"retrievalDatabaseUrl\":\"postgresql://user:pw@db.internal:5432/retrieval\"")
                .contains("\"aiEmbeddingModel\":\"google:gemini-embedding-001\"")
                .contains("\"aiEmbeddingDimensions\":768")
                .contains("\"geminiApiKey\":\"gemini-key-1\"")
                .contains("\"configVersion\":7");
    }

    @Test
    void blockedParsesReasonOn200() {
        factory.enqueue(HttpStatus.OK, "{\"applied\":false,\"blockedReason\":\"store probe failed\"}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(PAYLOAD);

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_BLOCKED);
        assertThat(result.message()).isEqualTo("store probe failed");
    }

    @Test
    void blockedBodyOnErrorStatusStillParsesReason() {
        factory.enqueue(HttpStatus.SERVICE_UNAVAILABLE, "{\"applied\":false,\"blockedReason\":\"stale version\"}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(PAYLOAD);

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_BLOCKED);
        assertThat(result.message()).isEqualTo("stale version");
    }

    @Test
    void plainHttpErrorIsFailed() {
        factory.enqueue(HttpStatus.NOT_FOUND, "{\"detail\":\"Not Found\"}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(PAYLOAD);

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_FAILED);
        assertThat(result.message()).contains("404");
    }

    @Test
    void transportFailureIsFailed() {
        factory.enqueueFailure(new IOException("boom"));

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(PAYLOAD);

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_FAILED);
    }

    /** 内存 stub：按队列回放响应，并记录最后一个请求的形状。 */
    private static final class StubFactory implements ClientHttpRequestFactory {

        private final Queue<Object> canned = new ArrayDeque<>();
        private HttpMethod lastMethod;
        private URI lastUri;
        private HttpHeaders lastHeaders;
        private String lastBody;

        void enqueue(HttpStatus status, String body) {
            canned.add(new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status));
        }

        void enqueueFailure(IOException failure) {
            canned.add(failure);
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            Object next = canned.remove();
            return new AbstractClientHttpRequest() {
                private final ByteArrayOutputStream body = new ByteArrayOutputStream();

                @Override
                protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                    lastMethod = method;
                    lastUri = uri;
                    lastHeaders = headers;
                    lastBody = body.toString(StandardCharsets.UTF_8);
                    if (next instanceof IOException failure) {
                        throw failure;
                    }
                    return (ClientHttpResponse) next;
                }

                @Override
                public HttpMethod getMethod() {
                    return method;
                }

                @Override
                public URI getURI() {
                    return uri;
                }

                @Override
                public java.io.OutputStream getBodyInternal(HttpHeaders headers) {
                    return body;
                }
            };
        }
    }
}
