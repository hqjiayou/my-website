package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentRuntimeConfigPayload;
import com.mywebsite.agent.dto.AgentRuntimeConfigPushResult;
import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

class HttpAgentClientTest {

    private HttpServer server;
    private AgentProperties properties;
    private HttpAgentClient client;
    private final AtomicInteger chatHits = new AtomicInteger();
    private final AtomicInteger healthHits = new AtomicInteger();
    private final AtomicInteger pushStatus = new AtomicInteger(200);
    private final java.util.concurrent.atomic.AtomicReference<String> pushResponseBody =
            new java.util.concurrent.atomic.AtomicReference<>("{\"applied\":true,\"appliedVersion\":7}");
    private final java.util.concurrent.atomic.AtomicReference<String> pushRequestBody =
            new java.util.concurrent.atomic.AtomicReference<>();

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
        server.createContext("/internal/v1/config/retrieval", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            if (!"secret-token".equals(token)) {
                write(exchange, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
                return;
            }
            pushRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, pushStatus.get(), pushResponseBody.get());
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
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .messageConverters(converters -> {
                    ObjectMapper mapper = new ObjectMapper();
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
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
    void runtimeStatusMapsHealthWithDefaultsForOlderAgentServices() {
        AgentRuntimeStatus status = client.runtimeStatus();
        assertThat(status.providerStatus()).isEqualTo("BLOCKED");
        assertThat(status.fallbackProviders()).isEmpty();
        assertThat(status.mcpServers()).isEmpty();
        assertThat(status.limits()).isEqualTo(AgentRuntimeStatus.Limits.UNKNOWN);
        assertThat(status.harness()).isEqualTo(AgentRuntimeStatus.HarnessFiles.UNKNOWN);
    }

    @Test
    void runtimeStatusMapsLimitsAndMcpServers() {
        server.removeContext("/internal/v1/health");
        server.createContext("/internal/v1/health", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"provider\":\"openai:gpt-4o\",\"providerStatus\":\"LIVE\","
                        + "\"fallbackProviders\":[\"anthropic:claude\"],\"mcpStatus\":\"CONFIGURED\","
                        + "\"mcpServers\":[{\"name\":\"echo\",\"enabled\":true}],"
                        + "\"limits\":{\"maxRequests\":10,\"maxToolCalls\":null,\"maxTotalTokens\":null,"
                        + "\"toolTimeoutSeconds\":30.0,\"historyMaxChars\":24000},"
                        + "\"harness\":{\"background\":true,\"guardrails\":false,\"toolGuidance\":true},"
                        + "\"timestamp\":\"t\"}"));

        AgentRuntimeStatus status = client.runtimeStatus();
        assertThat(status.fallbackProviders()).containsExactly("anthropic:claude");
        assertThat(status.mcpServers()).containsExactly(new AgentRuntimeStatus.McpServer("echo", true));
        assertThat(status.limits()).isEqualTo(new AgentRuntimeStatus.Limits(10, null, null, 30.0, 24000));
        assertThat(status.harness()).isEqualTo(new AgentRuntimeStatus.HarnessFiles(true, false, true));
    }

    @Test
    void mcpReadyReads503ProbeBody() throws IOException {
        server.createContext("/internal/v1/mcp/ready", exchange -> write(
                exchange,
                503,
                "{\"ok\":false,\"errorCode\":\"MCP_UNAVAILABLE\",\"mcpStatus\":\"CONFIGURED\","
                        + "\"message\":\"MCP server unreachable: dead\","
                        + "\"servers\":[{\"name\":\"dead\",\"ok\":false,\"error\":\"TimeoutError\"}]}"));

        AgentMcpReady ready = client.mcpReady();
        assertThat(ready.ok()).isFalse();
        assertThat(ready.errorCode()).isEqualTo("MCP_UNAVAILABLE");
        assertThat(ready.servers()).containsExactly(new AgentMcpReady.Server("dead", false, null, "TimeoutError"));
    }

    @Test
    void mcpReadyMapsDisabled() throws IOException {
        server.createContext("/internal/v1/mcp/ready", exchange -> write(
                exchange, 200, "{\"ok\":true,\"mcpStatus\":\"DISABLED\",\"servers\":[]}"));

        AgentMcpReady ready = client.mcpReady();
        assertThat(ready.ok()).isTrue();
        assertThat(ready.mcpStatus()).isEqualTo("DISABLED");
        assertThat(ready.servers()).isEmpty();
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
    void chatReadTimeoutDoesNotRetry() {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> {
            chatHits.incrementAndGet();
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            write(exchange, 200, "{\"ok\":true,\"data\":{\"reply\":\"late\",\"providerStatus\":\"LIVE\"}}");
        });
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofMillis(300));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .messageConverters(converters -> {
                    ObjectMapper mapper = new ObjectMapper();
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
                .build();
        HttpAgentClient slowClient = new HttpAgentClient(properties, new ObjectMapper(), restClient);

        assertThatThrownBy(() -> slowClient.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
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
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .messageConverters(converters -> {
                    ObjectMapper mapper = new ObjectMapper();
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
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
        assertThat(response.todos()).isEmpty();
        assertThat(response.skillsApplied()).isEmpty();
        assertThat(response.extractedMemories()).isEmpty();
    }

    @Test
    void liveSuccessMapsExtractedMemoriesAndToleratesMalformed() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"providerStatus\":\"LIVE\","
                        + "\"extractedMemories\":[\"likes tea\",null]}}"));

        AgentChatResponse response = client.chat(new AgentChatRequest("10", "hello"));
        assertThat(response.extractedMemories()).containsExactly("likes tea");

        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"extractedMemories\":\"oops\"}}"));
        assertThat(client.chat(new AgentChatRequest("10", "hello")).extractedMemories()).isEmpty();
    }

    @Test
    void legacyChatRequestOmitsMemoryFields() throws Exception {
        AgentChatRequest request = new AgentChatRequest("10", "hello");
        assertThat(request.memories()).isNull();
        assertThat(request.memoryEnabled()).isNull();
        String json = new ObjectMapper().writeValueAsString(request);
        assertThat(json).doesNotContain("memories").doesNotContain("memoryEnabled");
    }

    @Test
    void liveSuccessMapsTodosAndSkillsApplied() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"providerStatus\":\"LIVE\","
                        + "\"skillsApplied\":[\"orchestration\"],\"skillsMissing\":null,"
                        + "\"todos\":[{\"id\":\"t1\",\"title\":\"Step 1\",\"status\":\"IN_PROGRESS\",\"extra\":1}]}}"));

        AgentChatResponse response = client.chat(new AgentChatRequest("10", "hello"));
        assertThat(response.todos()).containsExactly(new AgentTodoItem("t1", "Step 1", "IN_PROGRESS"));
        assertThat(response.skillsApplied()).containsExactly("orchestration");
    }

    @Test
    void liveSuccessMapsUsageAndToolTrace() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"providerStatus\":\"LIVE\","
                        + "\"usage\":{\"requests\":2,\"toolCalls\":1,\"inputTokens\":30,\"outputTokens\":5},"
                        + "\"tools\":[{\"name\":\"list_todos\",\"ok\":true},{\"name\":\"mcp_x\",\"ok\":false}]}}"));

        AgentRunMeta meta = client.chat(new AgentChatRequest("10", "hello")).runMeta();
        assertThat(meta.usage()).isEqualTo(new AgentRunMeta.Usage(2, 1, 30, 5));
        assertThat(meta.tools())
                .containsExactly(
                        new AgentRunMeta.ToolCall("list_todos", true), new AgentRunMeta.ToolCall("mcp_x", false));
    }

    @Test
    void malformedRunMetaIsDroppedWithoutFailingReply() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"reply\":\"hi\",\"providerStatus\":\"LIVE\",\"usage\":\"oops\"}}"));

        AgentChatResponse response = client.chat(new AgentChatRequest("10", "hello"));
        assertThat(response.reply()).isEqualTo("hi");
        assertThat(response.runMeta()).isEqualTo(AgentRunMeta.EMPTY);
    }

    @Test
    void budgetExceededMapsToAgentBudgetExceededWithoutRetry() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> {
            chatHits.incrementAndGet();
            write(exchange, 422, "{\"ok\":false,\"errorCode\":\"AGENT_BUDGET_EXCEEDED\",\"message\":\"limit\"}");
        });

        assertThatThrownBy(() -> client.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.AGENT_BUDGET_EXCEEDED.getCode());
        assertThat(chatHits.get()).isEqualTo(1);
    }

    @Test
    void listSkillsMapsCatalog() throws IOException {
        server.createContext("/internal/v1/skills", exchange -> write(
                exchange,
                200,
                "{\"ok\":true,\"data\":{\"skills\":[{\"id\":\"orchestration\",\"description\":\"plan\",\"version\":\"1\"}]}}"));

        assertThat(client.listSkills()).containsExactly(new AgentSkillSummary("orchestration", "plan", "1"));
    }

    @Test
    void upsertMapsRetrievalNotConfiguredAheadOfBlockedProviderStatus() throws IOException {
        server.createContext("/internal/v1/retrieval/documents", exchange -> write(
                exchange,
                503,
                "{\"ok\":false,\"errorCode\":\"RETRIEVAL_NOT_CONFIGURED\",\"providerStatus\":\"BLOCKED\","
                        + "\"message\":\"Retrieval is not configured (missing RETRIEVAL_DATABASE_URL).\"}"));

        assertThatThrownBy(() -> client.upsertRetrievalDocument(new AgentRetrievalDocument("1", "kb:1", "t", "hello")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(ErrorCode.RETRIEVAL_NOT_CONFIGURED.getCode());
                    assertThat(business.getMessageArgs())
                            .containsExactly("Retrieval is not configured (missing RETRIEVAL_DATABASE_URL).");
                });
    }

    @Test
    void chatMapsMcpUnavailableAsExternalDependencyBlocked() throws IOException {
        server.removeContext("/internal/v1/chat");
        server.createContext("/internal/v1/chat", exchange -> write(
                exchange,
                503,
                "{\"ok\":false,\"errorCode\":\"MCP_UNAVAILABLE\",\"message\":\"MCP unavailable: boom\"}"));

        assertThatThrownBy(() -> client.chat(new AgentChatRequest("10", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
    }

    @Test
    void chatStreamForwardsSseEvents() throws IOException {
        server.createContext("/internal/v1/chat/stream", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            if (!"secret-token".equals(token)) {
                write(exchange, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
                return;
            }
            byte[] body = ("event: START\ndata: {\"agentId\":\"10\"}\n\n"
                            + "event: TEXT_DELTA\ndata: {\"text\":\"he\"}\n\n"
                            + "event: TEXT_DELTA\ndata: {\"text\":\"llo\"}\n\n"
                            + "event: DONE\ndata: {\"reply\":\"hello\",\"providerStatus\":\"LIVE\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        java.util.List<String> events = new java.util.ArrayList<>();
        client.chatStream(new AgentChatRequest("10", "hello"), (event, data) -> events.add(event + "|" + data));

        assertThat(events)
                .containsExactly(
                        "START|{\"agentId\":\"10\"}",
                        "TEXT_DELTA|{\"text\":\"he\"}",
                        "TEXT_DELTA|{\"text\":\"llo\"}",
                        "DONE|{\"reply\":\"hello\",\"providerStatus\":\"LIVE\"}");
    }

    @Test
    void chatStreamMapsBlockedErrorBody() throws IOException {
        server.createContext("/internal/v1/chat/stream", exchange -> write(
                exchange,
                503,
                "{\"ok\":false,\"errorCode\":\"EXTERNAL_DEPENDENCY_BLOCKED\",\"providerStatus\":\"BLOCKED\",\"message\":\"no key\"}"));

        assertThatThrownBy(() -> client.chatStream(new AgentChatRequest("10", "hello"), (event, data) -> {}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
    }

    @Test
    void chatStreamConnectFailureIsExternalDependencyBlocked() {
        AgentProperties closed = new AgentProperties();
        closed.setBaseUrl("http://127.0.0.1:59999");
        closed.setInternalServiceToken("secret-token");
        closed.setConnectTimeoutMs(500);
        closed.setReadTimeoutMs(500);
        HttpAgentClient closedClient = new HttpAgentClient(closed, new ObjectMapper());

        assertThatThrownBy(() -> closedClient.chatStream(new AgentChatRequest("10", "hello"), (event, data) -> {}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
    }

    @Test
    void searchRetrievalParsesRerankAppliedAndDefaultsToFalse() throws IOException {
        server.createContext("/internal/v1/retrieval/search", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (body.contains("reranked")) {
                write(exchange, 200, "{\"hits\":[{\"sourceId\":\"kb-1\",\"chunkIndex\":0,\"score\":0.9,"
                        + "\"content\":\"chunk\"}],\"degraded\":false,\"rerankApplied\":true}");
            } else {
                write(exchange, 200, "{\"hits\":[],\"degraded\":false}");
            }
        });

        var reranked = client.searchRetrieval(
                new com.mywebsite.agent.dto.AgentRetrievalSearchRequest("7", "reranked query", null, null, null, null));
        assertThat(reranked.rerankApplied()).isTrue();
        assertThat(reranked.hits()).hasSize(1);

        var plain = client.searchRetrieval(
                new com.mywebsite.agent.dto.AgentRetrievalSearchRequest("7", "plain query", null, null, null, null));
        assertThat(plain.rerankApplied()).isFalse();
    }

    @Test
    void prepareReembedMapsDimensionChangeRequired() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/internal/v1/retrieval/reembed/prepare", exchange -> {
            hits.incrementAndGet();
            write(
                    exchange,
                    409,
                    "{\"ok\":false,\"errorCode\":\"DIMENSION_CHANGE_REQUIRED\","
                            + "\"message\":\"dimensions differ\",\"dimensions\":8,\"columnDimensions\":3}");
        });

        assertThatThrownBy(() -> client.prepareReembed(
                        new AgentModelSelection("openai:gpt", "key", null, "openai:embed"), false))
                .isInstanceOf(AgentDimensionChangeRequiredException.class)
                .satisfies(ex -> {
                    AgentDimensionChangeRequiredException dimension =
                            (AgentDimensionChangeRequiredException) ex;
                    assertThat(dimension.dimensions()).isEqualTo(8);
                    assertThat(dimension.columnDimensions()).isEqualTo(3);
                    assertThat(dimension.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
                });
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void ocrPdfParsesEnvelopeData() throws IOException {
        server.createContext("/internal/v1/ocr/pdf", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("X-Internal-Service-Token");
            if (!"secret-token".equals(token)) {
                write(exchange, 401, "{\"ok\":false,\"errorCode\":\"UNAUTHORIZED\"}");
                return;
            }
            write(
                    exchange,
                    200,
                    "{\"ok\":true,\"data\":{\"text\":\"转写文本\",\"modelSpec\":\"google:gemini\"}}");
        });

        var result = client.ocrPdf(
                new com.mywebsite.agent.dto.AgentOcrPdfRequest(
                        new AgentModelSelection("google:gemini", "k", null), java.util.List.of(), "JVBERi0="));

        assertThat(result.text()).isEqualTo("转写文本");
        assertThat(result.modelSpec()).isEqualTo("google:gemini");
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

    @Test
    void chatRequestSerializesFallbackModelsOnlyWhenPresent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        AgentChatRequest withChain = new AgentChatRequest(
                "10",
                "hello",
                null,
                null,
                null,
                null,
                null,
                null,
                new AgentModelSelection("google:gemini-3-flash-preview", "primary-key", null),
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(new AgentModelSelection("openai:gpt-x", "backup-key", null)));
        String json = mapper.writeValueAsString(withChain);
        assertThat(json).contains("\"model\":{\"spec\":\"google:gemini-3-flash-preview\"");
        assertThat(json).contains("\"fallbackModels\":[{\"spec\":\"openai:gpt-x\",\"apiKey\":\"backup-key\"}]");

        String withoutChain = mapper.writeValueAsString(new AgentChatRequest("10", "hello"));
        assertThat(withoutChain).doesNotContain("fallbackModels");
    }

    @Test
    void chatRequestSerializesDocumentsOnlyWhenPresent() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        AgentChatRequest withDocuments = new AgentChatRequest(
                "10", "hello", null, null, null, null, null, null, null, null, null, null, null, null, null,
                java.util.List.of(new com.mywebsite.agent.dto.AgentDocumentContext("a.txt", "正文", true)));
        String json = mapper.writeValueAsString(withDocuments);
        assertThat(json).contains("\"documents\":[{\"name\":\"a.txt\",\"text\":\"正文\",\"truncated\":true}]");

        String withoutDocuments = mapper.writeValueAsString(new AgentChatRequest("10", "hello"));
        assertThat(withoutDocuments).doesNotContain("documents");
    }

    // ---- 运行配置推送（Issue #146 UoW-1）----

    private static AgentRuntimeConfigPayload samplePayload() {
        return new AgentRuntimeConfigPayload(
                "postgresql://user:pw@db.internal:5432/retrieval", "google:gemini-embedding-001", 768, "gemini-key-1", 7L);
    }

    @Test
    void pushRuntimeConfigAppliedReturnsVersionAndSendsPayload() {
        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(samplePayload());

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_APPLIED);
        assertThat(result.appliedVersion()).isEqualTo(7L);
        assertThat(pushRequestBody.get())
                .contains("\"retrievalDatabaseUrl\":\"postgresql://user:pw@db.internal:5432/retrieval\"")
                .contains("\"aiEmbeddingDimensions\":768")
                .contains("\"configVersion\":7");
    }

    @Test
    void pushRuntimeConfigBlockedCarriesReason() {
        pushResponseBody.set("{\"applied\":false,\"blockedReason\":\"store probe failed\"}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(samplePayload());

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_BLOCKED);
        assertThat(result.message()).isEqualTo("store probe failed");
    }

    @Test
    void pushRuntimeConfigHttpErrorIsFailedNotThrown() {
        pushStatus.set(404);
        pushResponseBody.set("{\"detail\":\"Not Found\"}");

        AgentRuntimeConfigPushResult result = client.pushRuntimeConfig(samplePayload());

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_FAILED);
        assertThat(result.message()).contains("404");
    }

    @Test
    void pushRuntimeConfigWithoutTokenIsFailedWithoutCalling() {
        AgentProperties noToken = new AgentProperties();
        noToken.setBaseUrl(properties.getBaseUrl());
        noToken.setInternalServiceToken("");
        HttpAgentClient unconfigured = new HttpAgentClient(noToken, new ObjectMapper(), null);

        AgentRuntimeConfigPushResult result = unconfigured.pushRuntimeConfig(samplePayload());

        assertThat(result.status()).isEqualTo(AgentRuntimeConfigPushResult.STATUS_FAILED);
        assertThat(pushRequestBody.get()).isNull();
    }
}
