package com.mywebsite.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentMcpReady;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentOcrImageRequest;
import com.mywebsite.agent.dto.AgentOcrPdfRequest;
import com.mywebsite.agent.dto.AgentOcrPdfResult;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentReembedPrepareRequest;
import com.mywebsite.agent.dto.AgentReembedPrepareResult;
import com.mywebsite.agent.dto.AgentRetrievalDocument;
import com.mywebsite.agent.dto.AgentRetrievalHit;
import com.mywebsite.agent.dto.AgentRetrievalSearchRequest;
import com.mywebsite.agent.dto.AgentRetrievalSearchResult;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.web.HttpRequestCorrelation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAgentClient implements AgentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAgentClient.class);

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public HttpAgentClient(AgentProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    /** 测试可注入自定义 RestClient；未注入时使用带 Jackson 转换器的默认实例。 */
    HttpAgentClient(AgentProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = configureObjectMapper(objectMapper);
        this.restClient = restClient != null ? restClient : buildRestClient(properties, this.objectMapper);
    }

    private static ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        // 新增响应字段（Python 先行扩展）不得打断 Java 解析
        return objectMapper
                .copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static RestClient buildRestClient(AgentProperties properties, ObjectMapper objectMapper) {
        // SimpleClientHttpRequestFactory（HttpURLConnection）对 JSON POST body 更可靠；
        // JdkClientHttpRequestFactory 在部分环境下会出现 body 未写出导致 FastAPI 422。
        SimpleClientHttpRequestFactory simpleFactory = new SimpleClientHttpRequestFactory();
        simpleFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        simpleFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        BufferingClientHttpRequestFactory requestFactory = new BufferingClientHttpRequestFactory(simpleFactory);
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }

    @Override
    public AgentPingResponse ping() {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("GET", "/internal/v1/health", null);
        String providerStatus = textOrNull(body, "providerStatus");
        String service = textOrNull(body, "service");
        String message = service != null ? service + " ready" : "agent-services ready";
        return new AgentPingResponse(message, providerStatus);
    }

    @Override
    public AgentRuntimeStatus runtimeStatus() {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("GET", "/internal/v1/health", null);
        try {
            // health 含 ok/service/timestamp 等观测字段；DTO 只取运行时子集
            return objectMapper
                    .readerFor(AgentRuntimeStatus.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(body);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("agent-services returned malformed health body", ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public AgentMcpReady mcpReady() {
        ensureTokenConfigured();
        // 503 + MCP_UNAVAILABLE 仍是一份完整探测报告，不能走 mapErrorBody（会丢掉 servers）
        String requestId = MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID);
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient
                    .get()
                    .uri("/internal/v1/mcp/ready")
                    .header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, properties.getInternalServiceToken());
            if (StringUtils.hasText(requestId)) {
                spec = spec.header(HttpRequestCorrelation.X_REQUEST_ID, requestId);
            }
            String raw = spec.retrieve().body(String.class);
            return readMcpReady(raw);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 503) {
                try {
                    return readMcpReady(ex.getResponseBodyAsString());
                } catch (BusinessException ignored) {
                    // 不是探测报告，按通用错误映射
                }
            }
            throw mapHttpError(ex);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private AgentMcpReady readMcpReady(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.has("mcpStatus") && !node.has("servers")) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            return objectMapper
                    .readerFor(AgentMcpReady.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(node);
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        if (request == null
                || !StringUtils.hasText(request.agentId())
                || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();

        JsonNode body = exchangeWithRetry("POST", "/internal/v1/chat", request);
        JsonNode data = body.path("data");
        if (!data.isObject()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        String reply = textOrNull(data, "reply");
        if (!StringUtils.hasText(reply)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        String conversationId = textOrNull(data, "conversationId");
        String providerStatus = textOrNull(data, "providerStatus");
        if (!"LIVE".equals(providerStatus)) {
            // 成功体不得携带非 LIVE 冒充真实接通
            throw new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
        }
        return new AgentChatResponse(
                reply,
                conversationId,
                providerStatus,
                readList(data.get("todos"), AgentTodoItem.class),
                readRunMeta(data),
                readList(data.get("citations"), AgentCitation.class),
                readExtractedMemories(data));
    }

    /** 缺失或格式异常时为空，不让已经成功的回复失败。 */
    private List<String> readExtractedMemories(JsonNode data) {
        JsonNode node = data.get("extractedMemories");
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            log.warn("agent-services returned malformed extractedMemories, ignored");
            return List.of();
        }
        try {
            List<String> values = objectMapper.convertValue(
                    node, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (values == null) {
                return List.of();
            }
            List<String> kept = new ArrayList<>();
            for (String value : values) {
                if (value != null) {
                    kept.add(value);
                }
            }
            return List.copyOf(kept);
        } catch (IllegalArgumentException ex) {
            log.warn("agent-services returned malformed extractedMemories, ignored", ex);
            return List.of();
        }
    }

    /** 观测元数据格式异常只丢弃元数据，不让已成功的回复失败（与 todos 不同：todos 影响编排正确性）。 */
    private AgentRunMeta readRunMeta(JsonNode data) {
        try {
            return objectMapper.treeToValue(data, AgentRunMeta.class);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("agent-services returned malformed run metadata, ignored", ex);
            return AgentRunMeta.EMPTY;
        }
    }

    @Override
    public List<AgentSkillSummary> listSkills() {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("GET", "/internal/v1/skills", null);
        return readList(body.path("data").get("skills"), AgentSkillSummary.class);
    }

    private <T> List<T> readList(JsonNode node, Class<T> elementType) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(
                    node, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IllegalArgumentException ex) {
            log.warn("agent-services returned malformed {} list", elementType.getSimpleName(), ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private void ensureTokenConfigured() {
        if (!StringUtils.hasText(properties.getInternalServiceToken())) {
            throw new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
        }
    }

    @Override
    public void upsertRetrievalDocument(AgentRetrievalDocument document) {
        if (document == null
                || !StringUtils.hasText(document.agentId())
                || !StringUtils.hasText(document.sourceId())
                || !StringUtils.hasText(document.content())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();
        exchangeWithRetry("PUT", "/internal/v1/retrieval/documents", document);
    }

    @Override
    public AgentReembedPrepareResult prepareReembed(AgentModelSelection selection, boolean confirmRecreate) {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry(
                "POST",
                "/internal/v1/retrieval/reembed/prepare",
                new AgentReembedPrepareRequest(selection, confirmRecreate));
        JsonNode dimensions = body.get("dimensions");
        if (dimensions == null || !dimensions.isNumber() || !body.has("recreated")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode column = body.get("columnDimensions");
        Integer columnDimensions = column != null && column.isNumber() ? column.asInt() : null;
        return new AgentReembedPrepareResult(dimensions.asInt(), columnDimensions, body.get("recreated").asBoolean());
    }

    @Override
    public AgentOcrPdfResult ocrPdf(AgentOcrPdfRequest request) {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("POST", "/internal/v1/ocr/pdf", request);
        JsonNode data = body.path("data");
        JsonNode text = data.get("text");
        if (text == null || !text.isTextual()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode spec = data.get("modelSpec");
        return new AgentOcrPdfResult(text.asText(), spec != null && spec.isTextual() ? spec.asText() : null);
    }

    @Override
    public AgentOcrPdfResult ocrImage(AgentOcrImageRequest request) {
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("POST", "/internal/v1/ocr/image", request);
        JsonNode data = body.path("data");
        JsonNode text = data.get("text");
        if (text == null || !text.isTextual()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode spec = data.get("modelSpec");
        return new AgentOcrPdfResult(text.asText(), spec != null && spec.isTextual() ? spec.asText() : null);
    }

    @Override
    public void deleteRetrievalDocument(String agentId, String sourceId) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(sourceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();
        String path = "/internal/v1/retrieval/documents?agentId="
                + java.net.URLEncoder.encode(agentId, StandardCharsets.UTF_8)
                + "&sourceId="
                + java.net.URLEncoder.encode(sourceId, StandardCharsets.UTF_8);
        exchangeWithRetry("DELETE", path, null);
    }

    @Override
    public AgentRetrievalSearchResult searchRetrieval(AgentRetrievalSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.agentId()) || !StringUtils.hasText(request.query())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();
        JsonNode body = exchangeWithRetry("POST", "/internal/v1/retrieval/search", request);
        // 契约体是 {hits, degraded}；若包在与 chat 相同的 {ok, data} 信封里，取 data
        JsonNode payload = body.has("hits") ? body : body.path("data");
        if (!payload.isObject()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return new AgentRetrievalSearchResult(
                readList(payload.get("hits"), AgentRetrievalHit.class),
                payload.path("degraded").asBoolean(false),
                payload.path("rerankApplied").asBoolean(false));
    }

    @Override
    public void chatStream(AgentChatRequest request, AgentStreamListener listener) {
        if (request == null
                || !StringUtils.hasText(request.agentId())
                || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();

        // SSE 必须逐块读取，BufferingClientHttpRequestFactory 会缓冲整个响应，故直接用 HttpURLConnection
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/internal/v1/chat/stream");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout((int) properties.getConnectTimeoutMs());
            connection.setReadTimeout((int) properties.getReadTimeoutMs());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, properties.getInternalServiceToken());
            String requestId = MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID);
            if (StringUtils.hasText(requestId)) {
                connection.setRequestProperty(HttpRequestCorrelation.X_REQUEST_ID, requestId);
            }
            connection.getOutputStream().write(objectMapper.writeValueAsBytes(request));

            int status = connection.getResponseCode();
            if (status != 200) {
                throw mapStreamHttpError(connection, status);
            }
            forwardSseEvents(connection.getInputStream(), listener);
        } catch (IOException ex) {
            log.warn("agent-services chat stream I/O failure", ex);
            throw new BusinessException(
                    isConnectFailure(ex) ? ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED : ErrorCode.SYSTEM_ERROR);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private BusinessException mapStreamHttpError(HttpURLConnection connection, int status) {
        try (InputStream errorStream = connection.getErrorStream()) {
            if (errorStream != null) {
                JsonNode node = objectMapper.readTree(errorStream.readAllBytes());
                return mapErrorBody(node, status);
            }
        } catch (IOException ignored) {
            // fall through
        }
        return new BusinessException(status == 503 ? ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED : ErrorCode.SYSTEM_ERROR);
    }

    private static void forwardSseEvents(InputStream inputStream, AgentStreamListener listener) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (eventName != null) {
                        listener.onEvent(eventName, data.toString());
                    }
                    eventName = null;
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
            }
            if (eventName != null) {
                listener.onEvent(eventName, data.toString());
            }
        }
    }

    private JsonNode exchangeWithRetry(String method, String path, Object body) {
        int maxAttempts = Math.max(0, properties.getRetryMaxAttempts()) + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return exchangeOnce(method, path, body);
            } catch (RetryableAgentCallException ex) {
                if (attempt >= maxAttempts) {
                    log.warn(
                            "agent-services retryable failure method={} path={} attempt={}",
                            method,
                            path,
                            attempt,
                            ex.getCause() != null ? ex.getCause() : ex);
                    throw ex.toBusinessException();
                }
                sleepJitter(attempt);
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    private JsonNode exchangeOnce(String method, String path, Object body) {
        String requestId = MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID);
        try {
            RestClient.RequestHeadersSpec<?> spec;
            if ("POST".equals(method) || "PUT".equals(method)) {
                RestClient.RequestBodyUriSpec bodySpec =
                        "POST".equals(method) ? restClient.post() : restClient.put();
                spec = bodySpec.uri(path).contentType(MediaType.APPLICATION_JSON).body(body);
            } else if ("DELETE".equals(method)) {
                spec = restClient.delete().uri(path);
            } else {
                spec = restClient.get().uri(path);
            }
            spec = spec.header(AgentHttpHeaders.INTERNAL_SERVICE_TOKEN, properties.getInternalServiceToken());
            if (StringUtils.hasText(requestId)) {
                spec = spec.header(HttpRequestCorrelation.X_REQUEST_ID, requestId);
            }

            String raw = spec.retrieve().body(String.class);
            if (!StringUtils.hasText(raw)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.path("ok").isBoolean() && !node.path("ok").asBoolean()) {
                throw mapErrorBody(node, 503);
            }
            return node;
        } catch (RestClientResponseException ex) {
            BusinessException mapped = mapHttpError(ex);
            int status = ex.getStatusCode().value();
            // 仅连接层之上的 502/504 可有限重试；503 blocked / 4xx 不重试
            if (status == 502 || status == 504) {
                throw new RetryableAgentCallException(mapped, ex);
            }
            throw mapped;
        } catch (RestClientException ex) {
            // POST 已经发出后的读超时可能表示服务端仍在执行（预约等）。重试会造成两次副作用。
            if ("POST".equals(method) && isReadTimeout(ex)) {
                log.warn("agent-services read timed out method={} path={}", method, path);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            throw new RetryableAgentCallException(
                    new BusinessException(
                            isConnectFailure(ex) ? ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED : ErrorCode.SYSTEM_ERROR),
                    ex);
        } catch (IOException ex) {
            throw new RetryableAgentCallException(
                    new BusinessException(ErrorCode.SYSTEM_ERROR),
                    new RestClientException("Failed to parse agent-services response", ex));
        }
    }

    private BusinessException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        JsonNode node = null;
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (StringUtils.hasText(responseBody)) {
                node = objectMapper.readTree(responseBody);
            }
        } catch (IOException ignored) {
            // fall through
        }
        if (node != null) {
            return mapErrorBody(node, status);
        }
        if (status == 401 || status == 403) {
            // 内部鉴权失败不对外暴露细节
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (status >= 500) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    private BusinessException mapErrorBody(JsonNode node, int httpStatus) {
        String errorCode = textOrNull(node, "errorCode");
        if ("DIMENSION_CHANGE_REQUIRED".equals(errorCode)) {
            return dimensionChangeRequired(node);
        }
        // MCP 不可用与 Provider BLOCKED 同属外部依赖失败（ADR-0004/§19.10）
        if ("EXTERNAL_DEPENDENCY_BLOCKED".equals(errorCode)
                || "MCP_UNAVAILABLE".equals(errorCode)
                || "BLOCKED".equals(textOrNull(node, "providerStatus"))) {
            return new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
        }
        // 运行预算超限（Python 422 + 明确 errorCode；勿把 FastAPI 校验 422 一律当成预算）
        if ("AGENT_BUDGET_EXCEEDED".equals(errorCode)) {
            return new BusinessException(ErrorCode.AGENT_BUDGET_EXCEEDED);
        }
        if ("BAD_REQUEST".equals(errorCode) || httpStatus == 400) {
            return new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if ("UNAUTHORIZED".equals(errorCode) || httpStatus == 401 || httpStatus == 403) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (httpStatus >= 500) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    private static BusinessException dimensionChangeRequired(JsonNode node) {
        JsonNode dimensions = node.get("dimensions");
        if (dimensions == null || !dimensions.isNumber()) {
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode column = node.get("columnDimensions");
        Integer columnDimensions = column != null && column.isNumber() ? column.asInt() : null;
        return new AgentDimensionChangeRequiredException(dimensions.asInt(), columnDimensions);
    }

    private static boolean isReadTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException timeout) {
                String message = timeout.getMessage();
                return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("read");
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleepJitter(int attempt) {
        try {
            long delay = 50L * attempt + ThreadLocalRandom.current().nextLong(40);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 可重试调用失败的内部信号；对外仍映射为 BusinessException。 */
    private static boolean isConnectFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class RetryableAgentCallException extends RuntimeException {
        private final BusinessException businessException;

        private RetryableAgentCallException(BusinessException businessException, Throwable cause) {
            super(cause);
            this.businessException = businessException;
        }

        private BusinessException toBusinessException() {
            return businessException;
        }
    }
}
