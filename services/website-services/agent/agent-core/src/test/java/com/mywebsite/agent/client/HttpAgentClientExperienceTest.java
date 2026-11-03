package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentExperienceExtractRequest;
import com.mywebsite.agent.dto.AgentExperienceExtractResult;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * 经验提炼客户端的 socket-free 契约测试（Issue #184 UoW-2）：以内存 stub 工厂喂 canned
 * 响应，验证请求形状（路径/token 头/turns 与 existingTitles 载荷）与候选/用量解析、
 * 坏数据与 BLOCKED 的失败映射。真实 HTTP 路径由 CI 的 HttpAgentClientTest 覆盖。
 */
class HttpAgentClientExperienceTest {

    private static final AgentExperienceExtractRequest REQUEST = new AgentExperienceExtractRequest(
            new AgentModelSelection("fake:key", "secret", null),
            List.of(),
            List.of(new AgentExperienceExtractRequest.Turn("101", "用户问题", "助手回答", "signals: TOOL_FAILURE")),
            List.of("已有教训"));

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
    void parsesCandidatesUsageAndModelSpec() {
        factory.enqueue(
                HttpStatus.OK,
                """
                {"ok":true,"data":{"candidates":[
                  {"sourceTurnId":"101","title":"工具失败不得编造","symptom":"症状","lesson":"失败时明示","evidence":"Turn 1"}],
                 "usage":{"requests":1,"toolCalls":0,"inputTokens":100,"outputTokens":50},
                 "modelSpec":"fake:key"}}
                """);

        AgentExperienceExtractResult result = client.extractExperience(REQUEST);

        assertThat(result.candidates()).hasSize(1);
        AgentExperienceExtractResult.Candidate candidate = result.candidates().get(0);
        assertThat(candidate.sourceTurnId()).isEqualTo("101");
        assertThat(candidate.title()).isEqualTo("工具失败不得编造");
        assertThat(candidate.lesson()).isEqualTo("失败时明示");
        assertThat(result.usage()).isNotNull();
        assertThat(result.usage().inputTokens()).isEqualTo(100);
        assertThat(result.usage().outputTokens()).isEqualTo(50);
        assertThat(result.modelSpec()).isEqualTo("fake:key");

        assertThat(factory.lastMethod).isEqualTo(HttpMethod.POST);
        assertThat(factory.lastUri.getPath()).isEqualTo("/internal/v1/experience/extract");
        assertThat(factory.lastHeaders.getFirst("X-Internal-Service-Token")).isEqualTo("secret-token");
        assertThat(factory.lastBody)
                .contains("\"spec\":\"fake:key\"")
                .contains("\"apiKey\":\"secret\"")
                .contains("\"turnId\":\"101\"")
                .contains("\"metaSummary\":\"signals: TOOL_FAILURE\"")
                .contains("\"existingTitles\":[\"已有教训\"]");
    }

    @Test
    void emptyCandidatesAndMissingUsageParseAsEmptyAndNull() {
        factory.enqueue(HttpStatus.OK, "{\"ok\":true,\"data\":{\"candidates\":[],\"modelSpec\":\"fake:key\"}}");

        AgentExperienceExtractResult result = client.extractExperience(REQUEST);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.usage()).isNull();
    }

    @Test
    void malformedDataIsSystemError() {
        factory.enqueue(HttpStatus.OK, "{\"ok\":true,\"data\":{\"modelSpec\":\"fake:key\"}}");

        assertThatThrownBy(() -> client.extractExperience(REQUEST))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode()));
    }

    @Test
    void blockedMapsToExternalDependencyBlocked() {
        factory.enqueue(
                HttpStatus.SERVICE_UNAVAILABLE,
                "{\"errorCode\":\"EXTERNAL_DEPENDENCY_BLOCKED\",\"message\":\"no model\",\"providerStatus\":\"BLOCKED\"}");

        assertThatThrownBy(() -> client.extractExperience(REQUEST))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
    }

    /** 内存 stub：按队列回放响应，并记录最后一个请求的形状（与 HttpAgentClientPushTest 同款）。 */
    private static final class StubFactory implements ClientHttpRequestFactory {

        private final Queue<Object> canned = new ArrayDeque<>();
        private HttpMethod lastMethod;
        private URI lastUri;
        private HttpHeaders lastHeaders;
        private String lastBody;

        void enqueue(HttpStatus status, String body) {
            MockClientHttpResponse response =
                    new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
            // 与执行层实际响应一致：application/json; charset=UTF-8，否则 String 按 Latin-1 解码乱码。
            response.getHeaders().setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            canned.add(response);
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
