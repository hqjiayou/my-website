package com.mywebsite.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.testsupport.AbstractAgentSpringBootTest;
import com.mywebsite.infra.crypto.ApiKeyHasher;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 对外 API 网关 /v1（UoW-2）：鉴权、alias 解析、非流式/流式转发与备援。 */
@AutoConfigureMockMvc
class GatewayV1MvcTest extends AbstractAgentSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String VALID_KEY = "mwgw_valid-test-key";
    private static final String REVOKED_KEY = "mwgw_revoked-test-key";
    private static final String EXPIRED_KEY = "mwgw_expired-test-key";

    private HttpServer upstream;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM gateway_access_key");
        jdbcTemplate.update("DELETE FROM agent_model_entry");
        jdbcTemplate.update("DELETE FROM agent_ai_usage");
        insertAccessKey("valid", VALID_KEY, null, null);
        insertAccessKey("revoked", REVOKED_KEY, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);
        insertAccessKey(
                "expired", EXPIRED_KEY, null, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    }

    @AfterEach
    void tearDown() {
        if (upstream != null) {
            upstream.stop(0);
            upstream = null;
        }
    }

    // ---- 鉴权过滤器：五种失败 + 一种成功 ----

    @Test
    void missingAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"))
                .andExpect(jsonPath("$.error.type").value("authentication_error"));
    }

    @Test
    void wrongPrefixTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer sk-not-our-prefix"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void unknownKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer mwgw_never-issued"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void revokedKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer " + REVOKED_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void expiredKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void validKeyIsAllowed() throws Exception {
        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY))
                .andExpect(status().isOk());
    }

    // ---- /v1/models ----

    @Test
    void modelsListsOnlyEnabledEntriesWithAlias() throws Exception {
        insertModelEntry("gpt-enabled", "openai:gpt-4o-mini", "k1", null, null, true, "gpt-enabled-alias");
        insertModelEntry("gpt-disabled", "openai:gpt-4o-mini", "k2", null, null, false, "gpt-disabled-alias");
        insertModelEntry("gpt-no-alias", "openai:gpt-4o-mini", "k3", null, null, true, null);

        mockMvc.perform(get("/v1/models").header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("gpt-enabled-alias"))
                .andExpect(jsonPath("$.data[0].object").value("model"))
                .andExpect(jsonPath("$.data[0].owned_by").value("my-website"));
    }

    // ---- alias 解析 ----

    @Test
    void chatCompletionsWithUnknownAliasIs404() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"no-such-alias\",\"messages\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("model_not_found"));
    }

    @Test
    void chatCompletionsWithNonOpenAiEntryIs400() throws Exception {
        insertModelEntry("claude", "anthropic:claude-3-opus", "k", null, null, true, "claude-alias");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-alias\",\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("model_not_supported"));
    }

    // ---- 非流式转发 ----

    @Test
    void chatCompletionsForwardsModelSubstitutionAndDecryptedKey() throws Exception {
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        int port = startStub("/chat/completions", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"id\":\"chatcmpl-1\",\"choices\":[{\"message\":{\"content\":\"hi\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        insertModelEntry(
                "gpt", "openai:gpt-4o-mini", "upstream-plain-key", "http://127.0.0.1:" + port, null, true, "gpt-alias");

        String body = mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-alias\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(capturedAuth.get()).isEqualTo("Bearer upstream-plain-key");
        JsonNode sentToUpstream = new ObjectMapper().readTree(capturedBody.get());
        assertThat(sentToUpstream.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(sentToUpstream.path("messages").get(0).path("content").asText()).isEqualTo("hi");
        assertThat(body).contains("chatcmpl-1");
    }

    // ---- 备援 ----

    @Test
    void chatCompletionsFallsBackWhenPrimaryReturns429() throws Exception {
        AtomicReference<Integer> primaryHits = new AtomicReference<>(0);
        int primaryPort = startStub("/chat/completions", exchange -> {
            primaryHits.updateAndGet(v -> v + 1);
            byte[] resp = "{\"error\":{\"message\":\"rate limited\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        AtomicReference<String> fallbackBody = new AtomicReference<>();
        HttpServer fallbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fallbackServer.createContext("/chat/completions", exchange -> {
            fallbackBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"id\":\"chatcmpl-fallback\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        fallbackServer.start();
        try {
            insertModelEntry(
                    "primary",
                    "openai:gpt-4o-primary",
                    "k-primary",
                    "http://127.0.0.1:" + primaryPort,
                    null,
                    true,
                    "primary-alias");
            insertModelEntry(
                    "fallback",
                    "openai:gpt-4o-fallback",
                    "k-fallback",
                    "http://127.0.0.1:" + fallbackServer.getAddress().getPort(),
                    null,
                    true,
                    null);

            String body = mockMvc.perform(post("/v1/chat/completions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"primary-alias\",\"messages\":[]}"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(primaryHits.get()).isEqualTo(1);
            assertThat(body).contains("chatcmpl-fallback");
            JsonNode sentToFallback = new ObjectMapper().readTree(fallbackBody.get());
            assertThat(sentToFallback.path("model").asText()).isEqualTo("gpt-4o-fallback");
        } finally {
            fallbackServer.stop(0);
        }
    }

    // ---- 流式 ----

    @Test
    void chatCompletionsStreamForwardsRawSseChunks() throws Exception {
        int port = startStub("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write("data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            }
        });
        insertModelEntry(
                "gpt-stream",
                "openai:gpt-4o-mini",
                "upstream-plain-key",
                "http://127.0.0.1:" + port,
                null,
                true,
                "gpt-stream-alias");

        String body = mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-stream-alias\",\"stream\":true,\"messages\":[]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
                        "data: [DONE]");
    }

    // ---- 用量记账（#140） ----

    @Test
    void chatCompletionsRecordsUsageWithTokensFromUpstreamBody() throws Exception {
        int port = startStub("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = ("{\"id\":\"chatcmpl-1\",\"choices\":[],"
                            + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":9,\"total_tokens\":30}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        insertModelEntry(
                "gpt", "openai:gpt-4o-mini", "upstream-plain-key", "http://127.0.0.1:" + port, null, true, "gpt-alias");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-alias\",\"messages\":[]}"))
                .andExpect(status().isOk());

        var rows = jdbcTemplate.queryForList("SELECT * FROM agent_ai_usage");
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("SOURCE")).isEqualTo("GATEWAY_V1");
        assertThat(row.get("ALIAS")).isEqualTo("gpt-alias");
        assertThat(row.get("STATUS")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("PROMPT_TOKENS")).longValue()).isEqualTo(21L);
        assertThat(((Number) row.get("COMPLETION_TOKENS")).longValue()).isEqualTo(9L);
        Long entryId = jdbcTemplate.queryForObject(
                "SELECT id FROM agent_model_entry WHERE alias = 'gpt-alias'", Long.class);
        assertThat(((Number) row.get("ENTRY_ID")).longValue()).isEqualTo(entryId);
        Long keyId = jdbcTemplate.queryForObject(
                "SELECT id FROM gateway_access_key WHERE name = 'valid'", Long.class);
        assertThat(((Number) row.get("GATEWAY_ACCESS_KEY_ID")).longValue()).isEqualTo(keyId);
        assertThat(row.get("LATENCY_MS")).isNotNull();
    }

    @Test
    void chatCompletionsRecordsUpstreamErrorWhenUpstreamFailsFinally() throws Exception {
        int port = startStub("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":{\"message\":\"boom\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        insertModelEntry(
                "gpt", "openai:gpt-4o-mini", "upstream-plain-key", "http://127.0.0.1:" + port, null, true, "gpt-alias");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-alias\",\"messages\":[]}"))
                .andExpect(status().isInternalServerError());

        var rows = jdbcTemplate.queryForList("SELECT * FROM agent_ai_usage");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("STATUS")).isEqualTo("UPSTREAM_ERROR");
        assertThat(rows.get(0).get("PROMPT_TOKENS")).isNull();
        assertThat(rows.get(0).get("COMPLETION_TOKENS")).isNull();
    }

    @Test
    void chatCompletionsStreamRecordsUsageFromTailChunkAndRequestsIncludeUsage() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        int port = startStub("/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write(("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7,"
                                + "\"total_tokens\":18}}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            }
        });
        insertModelEntry(
                "gpt-stream",
                "openai:gpt-4o-mini",
                "upstream-plain-key",
                "http://127.0.0.1:" + port,
                null,
                true,
                "gpt-stream-alias");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-stream-alias\",\"stream\":true,\"messages\":[]}"))
                .andExpect(status().isOk());

        JsonNode sentToUpstream = new ObjectMapper().readTree(capturedBody.get());
        assertThat(sentToUpstream.path("stream_options").path("include_usage").asBoolean()).isTrue();

        var rows = jdbcTemplate.queryForList("SELECT * FROM agent_ai_usage");
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("STATUS")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("PROMPT_TOKENS")).longValue()).isEqualTo(11L);
        assertThat(((Number) row.get("COMPLETION_TOKENS")).longValue()).isEqualTo(7L);
    }

    private interface StubHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException;
    }

    private int startStub(String path, StubHandler handler) throws java.io.IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext(path, handler::handle);
        upstream.start();
        return upstream.getAddress().getPort();
    }

    private void insertAccessKey(String name, String plainKey, OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO gateway_access_key (name, key_hash, key_hint, expires_at, revoked_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                name,
                ApiKeyHasher.sha256Hex(plainKey),
                plainKey.substring(plainKey.length() - 4),
                expiresAt,
                revokedAt);
    }

    /** {@code apiKey} 存明文（测试 profile 未配置 AI_MODEL_ENTRY_SECRET，解密直接原样返回）。 */
    private void insertModelEntry(
            String displayName,
            String modelSpec,
            String apiKey,
            String baseUrl,
            Long agentId,
            boolean enabled,
            String alias) {
        jdbcTemplate.update(
                "INSERT INTO agent_model_entry "
                        + "(display_name, model_spec, api_key, base_url, agent_id, enabled, is_default, mode, alias) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'KEY', ?)",
                displayName,
                modelSpec,
                apiKey,
                baseUrl,
                agentId,
                enabled,
                false,
                alias);
    }
}
