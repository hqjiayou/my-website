package com.mywebsite.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.AgentHttpHeaders;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.web.HttpRequestCorrelation;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(properties);
    }

    /** 测试可注入自定义 RestClient。 */
    HttpAgentClient(AgentProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(AgentProperties properties) {
        // JdkClientHttpRequestFactory（Spring 6.2）仅有 read timeout；connect 走 HttpClient
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
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
    public AgentChatResponse chat(AgentChatRequest request) {
        if (request == null
                || !StringUtils.hasText(request.siteId())
                || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        ensureTokenConfigured();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("siteId", request.siteId());
        payload.put("message", request.message());
        payload.put("locale", request.locale());
        payload.put("conversationId", request.conversationId());
        if (request.promptProfile() != null) {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("systemPrompt", request.promptProfile().systemPrompt());
            profile.put("temperature", request.promptProfile().temperature());
            payload.put("promptProfile", profile);
        } else {
            payload.put("promptProfile", null);
        }

        JsonNode body = exchangeWithRetry("POST", "/internal/v1/chat", payload);
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
        return new AgentChatResponse(reply, conversationId, providerStatus);
    }

    private void ensureTokenConfigured() {
        if (!StringUtils.hasText(properties.getInternalServiceToken())) {
            throw new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
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
            if ("POST".equals(method)) {
                spec = restClient
                        .post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
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
            throw new RetryableAgentCallException(new BusinessException(ErrorCode.SYSTEM_ERROR), ex);
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
        if ("EXTERNAL_DEPENDENCY_BLOCKED".equals(errorCode)
                || "BLOCKED".equals(textOrNull(node, "providerStatus"))) {
            return new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED);
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
