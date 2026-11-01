package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.dto.GatewayV1ErrorDto;
import com.mywebsite.agent.dto.GatewayV1ModelDtos;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.service.GatewayV1ErrorException;
import com.mywebsite.agent.service.GatewayV1Service;
import com.mywebsite.agent.utils.AgentModelChainUtils;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 对外 API 网关 {@code /v1/**} 转发：alias → 入口 → 组鏈备援 → 转发上游（仅支持
 * {@code openai:} 形状入口，第一期）。鉴权在 {@link com.mywebsite.agent.security.GatewayAccessKeyAuthFilter}
 * 做完，这里不重复鉴权。
 *
 * <p>用量记账（Issue #140）：每条客户端请求记一行，归属最终应答（或最终失败）的
 * 入口；非流式 tokens 取最终响应体的 usage，流式靠请求时注入
 * {@code stream_options.include_usage} 后扫描上游尾帧的 usage，拿不到记
 * null、不记 0。本地拒绝（alias 不存在/不支持）发生在上游调用之前，不记账。
 */
@Service
public class GatewayV1ServiceImpl implements GatewayV1Service {

    private static final Logger log = LoggerFactory.getLogger(GatewayV1ServiceImpl.class);

    private static final String OPENAI_PREFIX = "openai:";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    /** 流式 usage 扫描只保留尾部一小段文本，防长流把整段响应攒在内存里。 */
    private static final int STREAM_TAIL_CHARS = 16_384;

    private static final Pattern PROMPT_TOKENS_PATTERN = Pattern.compile("\"prompt_tokens\"\\s*:\\s*(\\d+)");
    private static final Pattern COMPLETION_TOKENS_PATTERN = Pattern.compile("\"completion_tokens\"\\s*:\\s*(\\d+)");

    private final AgentAiModelEntryMapper modelEntryMapper;
    private final ModelEntryCipher modelEntryCipher;
    private final ObjectMapper objectMapper;
    private final AgentUsageService usageService;

    public GatewayV1ServiceImpl(
            AgentAiModelEntryMapper modelEntryMapper,
            ModelEntryCipher modelEntryCipher,
            ObjectMapper objectMapper,
            AgentUsageService usageService) {
        this.modelEntryMapper = modelEntryMapper;
        this.modelEntryCipher = modelEntryCipher;
        this.objectMapper = objectMapper;
        this.usageService = usageService;
    }

    @Override
    public GatewayV1ModelDtos.ListResponse listModels() {
        List<AgentAiModelEntryEntity> entries = modelEntryMapper.selectList(
                new LambdaQueryWrapper<AgentAiModelEntryEntity>()
                        .eq(AgentAiModelEntryEntity::getEnabled, true)
                        .isNotNull(AgentAiModelEntryEntity::getAlias)
                        .orderByAsc(AgentAiModelEntryEntity::getId));
        List<GatewayV1ModelDtos.Model> models = (entries == null ? List.<AgentAiModelEntryEntity>of() : entries)
                .stream()
                .map(AgentAiModelEntryEntity::getAlias)
                .filter(StringUtils::hasText)
                .map(alias -> new GatewayV1ModelDtos.Model(alias.trim()))
                .toList();
        return new GatewayV1ModelDtos.ListResponse(models);
    }

    @Override
    public UpstreamResult chatCompletion(ObjectNode requestBody, Long gatewayAccessKeyId) {
        long startedNanos = System.nanoTime();
        String alias = requestedAlias(requestBody);
        AgentAiModelEntryEntity primary = resolveAliasEntry(requestBody);
        List<GatewayChainLink> chain = buildOpenAiChain(primary);
        if (chain.isEmpty()) {
            recordGatewayUsage(
                    alias, primary.getId(), primary.getAgentId(), AgentModelChainUtils.entryMode(primary),
                    null, null, startedNanos, AgentUsageService.STATUS_UPSTREAM_ERROR, gatewayAccessKeyId);
            return upstreamUnavailableResult();
        }
        UpstreamResult last = null;
        GatewayChainLink lastLink = null;
        for (int i = 0; i < chain.size(); i++) {
            GatewayChainLink link = chain.get(i);
            UpstreamResult result = callUpstreamOnce(link.selection(), requestBody);
            if (result.status() < 0) {
                // 连接失败：没有真实上游状态/错误体，视同可重试，耗尽链后合成失败体。
                last = null;
                lastLink = link;
                if (i < chain.size() - 1) {
                    continue;
                }
                recordGatewayUsage(
                        alias, link, null, null, startedNanos,
                        AgentUsageService.STATUS_UPSTREAM_ERROR, gatewayAccessKeyId);
                return upstreamUnavailableResult();
            }
            last = result;
            lastLink = link;
            boolean retryable = result.status() == 429 || result.status() >= 500;
            if (!retryable || i == chain.size() - 1) {
                Long[] tokens = parseUsage(result.body());
                recordGatewayUsage(
                        alias, link, tokens[0], tokens[1], startedNanos,
                        result.status() >= 200 && result.status() < 300
                                ? AgentUsageService.STATUS_SUCCESS
                                : AgentUsageService.STATUS_UPSTREAM_ERROR,
                        gatewayAccessKeyId);
                return result;
            }
        }
        // 循环保证有出口；走到这里仅为兜底，记到最后一个尝试过的入口。
        recordGatewayUsage(
                alias, lastLink, null, null, startedNanos,
                AgentUsageService.STATUS_UPSTREAM_ERROR, gatewayAccessKeyId);
        return last != null ? last : upstreamUnavailableResult();
    }

    @Override
    public void chatCompletionStream(ObjectNode requestBody, HttpServletResponse response, Long gatewayAccessKeyId)
            throws IOException {
        long startedNanos = System.nanoTime();
        String alias = requestedAlias(requestBody);
        AgentAiModelEntryEntity primary = resolveAliasEntry(requestBody);
        ensureIncludeUsage(requestBody);
        List<GatewayChainLink> chain = buildOpenAiChain(primary);
        if (chain.isEmpty()) {
            recordGatewayUsage(
                    alias, primary.getId(), primary.getAgentId(), AgentModelChainUtils.entryMode(primary),
                    null, null, startedNanos, AgentUsageService.STATUS_UPSTREAM_ERROR, gatewayAccessKeyId);
            writeJsonError(response, 502, "api_error", "upstream_unavailable", "Model entry key is not available");
            return;
        }
        for (int i = 0; i < chain.size(); i++) {
            GatewayChainLink link = chain.get(i);
            StreamAttempt attempt = streamOnce(link.selection(), requestBody, response);
            if (attempt.startedWriting()) {
                // 已经开始往客户端写字节：无论正常结束还是中途中断，都不再切换备援。
                recordGatewayUsage(
                        alias, link, attempt.promptTokens(), attempt.completionTokens(), startedNanos,
                        attempt.interrupted()
                                ? AgentUsageService.STATUS_UPSTREAM_ERROR
                                : AgentUsageService.STATUS_SUCCESS,
                        gatewayAccessKeyId);
                return;
            }
            boolean retryable =
                    attempt.preStartStatus() == null || attempt.preStartStatus() == 429 || attempt.preStartStatus() >= 500;
            if (retryable && i < chain.size() - 1) {
                continue;
            }
            recordGatewayUsage(
                    alias, link, null, null, startedNanos,
                    AgentUsageService.STATUS_UPSTREAM_ERROR, gatewayAccessKeyId);
            writeFinalStreamFailure(response, attempt);
            return;
        }
    }

    /** 请求原文里的 alias（model 字段）；转发会原地改写 model，记账须先留住原文。 */
    private static String requestedAlias(ObjectNode requestBody) {
        String alias = requestBody == null ? null : requestBody.path("model").asText(null);
        return StringUtils.hasText(alias) ? alias.trim() : null;
    }

    /** 流式默认请上游在尾帧带 usage；客户端自带 stream_options 时尊重原样、不改写。 */
    private void ensureIncludeUsage(ObjectNode requestBody) {
        if (requestBody == null) {
            return;
        }
        JsonNode streamOptions = requestBody.get("stream_options");
        if (streamOptions == null || streamOptions.isNull()) {
            ObjectNode options = objectMapper.createObjectNode();
            options.put("include_usage", true);
            requestBody.set("stream_options", options);
        }
    }

    /** 非流式响应体的 usage；解析不到返回 {null, null}，记账记 NULL 不记 0。 */
    private Long[] parseUsage(String body) {
        if (!StringUtils.hasText(body)) {
            return new Long[] {null, null};
        }
        try {
            JsonNode usage = objectMapper.readTree(body).path("usage");
            if (!usage.isObject()) {
                return new Long[] {null, null};
            }
            return new Long[] {longOrNull(usage.get("prompt_tokens")), longOrNull(usage.get("completion_tokens"))};
        } catch (JsonProcessingException | RuntimeException ex) {
            return new Long[] {null, null};
        }
    }

    private static Long longOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asLong() : null;
    }

    private void recordGatewayUsage(
            String alias,
            GatewayChainLink link,
            Long promptTokens,
            Long completionTokens,
            long startedNanos,
            String status,
            Long gatewayAccessKeyId) {
        recordGatewayUsage(
                alias,
                link == null ? null : link.entryId(),
                link == null ? null : link.agentId(),
                link == null ? null : link.mode(),
                promptTokens,
                completionTokens,
                startedNanos,
                status,
                gatewayAccessKeyId);
    }

    private void recordGatewayUsage(
            String alias,
            Long entryId,
            Long agentId,
            String mode,
            Long promptTokens,
            Long completionTokens,
            long startedNanos,
            String status,
            Long gatewayAccessKeyId) {
        usageService.recordUsage(new AgentUsageRecord(
                agentId,
                entryId,
                alias,
                mode,
                AgentUsageService.SOURCE_GATEWAY_V1,
                promptTokens,
                completionTokens,
                (System.nanoTime() - startedNanos) / 1_000_000L,
                status,
                gatewayAccessKeyId));
    }

    /** alias 精确匹配、enabled、未删除；禁用视同查无。只支持 {@code openai:} 形状入口。 */
    private AgentAiModelEntryEntity resolveAliasEntry(ObjectNode requestBody) {
        String alias = requestBody == null ? null : requestBody.path("model").asText(null);
        if (!StringUtils.hasText(alias)) {
            throw new GatewayV1ErrorException(404, "invalid_request_error", "model_not_found", "Model not found");
        }
        String trimmedAlias = alias.trim();
        AgentAiModelEntryEntity entry = modelEntryMapper.selectOne(new LambdaQueryWrapper<AgentAiModelEntryEntity>()
                .eq(AgentAiModelEntryEntity::getAlias, trimmedAlias)
                .eq(AgentAiModelEntryEntity::getEnabled, true)
                .last("LIMIT 1"));
        if (entry == null) {
            throw new GatewayV1ErrorException(
                    404, "invalid_request_error", "model_not_found", "Model '" + trimmedAlias + "' not found");
        }
        String modelSpec = entry.getModelSpec() == null ? "" : entry.getModelSpec().trim();
        if (!modelSpec.startsWith(OPENAI_PREFIX)) {
            throw new GatewayV1ErrorException(
                    400,
                    "invalid_request_error",
                    "model_not_supported",
                    "Model '" + trimmedAlias
                            + "' is not supported in this phase; only OpenAI-compatible model entries can be"
                            + " reached through /v1");
        }
        return entry;
    }

    /** 链上的一条：入口归属（记账用）＋转发凭证。 */
    private record GatewayChainLink(Long entryId, Long agentId, String mode, AgentModelSelection selection) {}

    /**
     * alias 命中入口为主用（不要求其为默认入口），备援沿用 {@link AgentModelChainUtils} 的
     * 作用域规则（主用的 agentId 作用域 × mode），只保留 {@code openai:} 形状（混合 provider
     * 的备援走不了同一套上游协议）。与改造前的 {@code buildOpenAiChain} 同规则，只是
     * 保留每条的入口 id／agentId／mode 供记账归属。
     *
     * <p>主用解密不出 key 时直接返回空链：客户端用 alias 显式点了这个模型，key 坏掉不能静默换成
     * 备援顶替成另一个模型去回应（与站点 chat {@code loadModelChain} 的「坏主用不被顶替」同一立场）。
     */
    private List<GatewayChainLink> buildOpenAiChain(AgentAiModelEntryEntity primary) {
        AgentModelSelection primarySelection = AgentModelChainUtils.toModelSelection(primary, modelEntryCipher);
        if (primarySelection == null) {
            return List.of();
        }
        List<GatewayChainLink> chain = new ArrayList<>();
        chain.add(linkOf(primary, primarySelection));
        String mode = AgentModelChainUtils.entryMode(primary);
        Long agentId = primary.getAgentId();
        if (agentId != null) {
            appendLinks(
                    chain,
                    AgentModelChainUtils.enabledModelEntries(modelEntryMapper, agentId).stream()
                            .filter(entry -> mode.equals(AgentModelChainUtils.entryMode(entry)))
                            .toList(),
                    primary.getId());
        }
        appendLinks(
                chain,
                AgentModelChainUtils.enabledModelEntries(modelEntryMapper, null).stream()
                        .filter(entry -> mode.equals(AgentModelChainUtils.entryMode(entry)))
                        .toList(),
                primary.getId());
        return chain.stream()
                .filter(link -> link.selection().spec().startsWith(OPENAI_PREFIX))
                .toList();
    }

    private void appendLinks(List<GatewayChainLink> chain, List<AgentAiModelEntryEntity> entries, Long skipId) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = AgentModelChainUtils.toModelSelection(entry, modelEntryCipher);
            if (selection != null) {
                chain.add(linkOf(entry, selection));
            }
        }
    }

    private static GatewayChainLink linkOf(AgentAiModelEntryEntity entry, AgentModelSelection selection) {
        return new GatewayChainLink(
                entry.getId(), entry.getAgentId(), AgentModelChainUtils.entryMode(entry), selection);
    }

    private static String upstreamModelName(AgentModelSelection selection) {
        return selection.spec().substring(OPENAI_PREFIX.length()).trim();
    }

    private static String resolveBaseUrl(AgentModelSelection selection) {
        return StringUtils.hasText(selection.baseUrl()) ? selection.baseUrl().trim() : DEFAULT_OPENAI_BASE_URL;
    }

    private UpstreamResult callUpstreamOnce(AgentModelSelection selection, ObjectNode requestBody) {
        requestBody.put("model", upstreamModelName(selection));
        String baseUrl = resolveBaseUrl(selection);
        try {
            return buildRestClient(baseUrl)
                    .post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + selection.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((req, resp) -> new UpstreamResult(
                            resp.getStatusCode().value(),
                            resp.getHeaders().getContentType() == null
                                    ? MediaType.APPLICATION_JSON_VALUE
                                    : resp.getHeaders().getContentType().toString(),
                            StreamUtils.copyToString(resp.getBody(), StandardCharsets.UTF_8)));
        } catch (RestClientException ex) {
            log.warn("gateway v1 upstream call failed baseUrl={}", baseUrl, ex);
            return new UpstreamResult(-1, MediaType.APPLICATION_JSON_VALUE, null);
        }
    }

    private static RestClient buildRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .build();
    }

    private UpstreamResult upstreamUnavailableResult() {
        return new UpstreamResult(502, MediaType.APPLICATION_JSON_VALUE, errorJson("Upstream connection failed"));
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(
                    GatewayV1ErrorDto.of(message, "api_error", "upstream_unavailable"));
        } catch (JsonProcessingException ex) {
            return "{\"error\":{\"message\":\"upstream unavailable\",\"type\":\"api_error\","
                    + "\"code\":\"upstream_unavailable\"}}";
        }
    }

    /**
     * 已写出字节即 {@code startedWriting}；否则带上游预响应状态/错误体（连接失败两者皆 null）。
     * started 时附带尾帧扫到的 usage（拿不到为 null）与是否中途中断。
     */
    private record StreamAttempt(
            boolean startedWriting,
            Integer preStartStatus,
            byte[] preStartErrorBody,
            String preStartContentType,
            Long promptTokens,
            Long completionTokens,
            boolean interrupted) {

        static StreamAttempt started(Long promptTokens, Long completionTokens, boolean interrupted) {
            return new StreamAttempt(true, null, null, null, promptTokens, completionTokens, interrupted);
        }

        static StreamAttempt connectFailed() {
            return new StreamAttempt(false, null, null, null, null, null, false);
        }

        static StreamAttempt notStarted(int status, byte[] body, String contentType) {
            return new StreamAttempt(false, status, body, contentType, null, null, false);
        }
    }

    private StreamAttempt streamOnce(AgentModelSelection selection, ObjectNode requestBody, HttpServletResponse response) {
        requestBody.put("model", upstreamModelName(selection));
        String baseUrl = resolveBaseUrl(selection);
        HttpURLConnection connection = null;
        boolean responseStarted = false;
        StringBuilder tail = new StringBuilder();
        try {
            URI uri = URI.create(baseUrl + "/chat/completions");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + selection.apiKey());
            connection.getOutputStream().write(objectMapper.writeValueAsBytes(requestBody));

            int status = connection.getResponseCode();
            if (status != 200) {
                return StreamAttempt.notStarted(status, readAllOrEmpty(connection.getErrorStream()), connection.getContentType());
            }
            responseStarted = true;
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            try (InputStream upstream = connection.getInputStream();
                    OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = upstream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                    // 记账旁路：只留尾部一小段扫 usage，不改透传字节与时序。
                    appendTail(tail, new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
            Long[] tokens = scanTailUsage(tail);
            return StreamAttempt.started(tokens[0], tokens[1], false);
        } catch (IOException ex) {
            if (responseStarted) {
                // 已经把 200 提交给客户端、正在转发字节时中断：按规则直接结束流，不切换备援。
                log.warn("gateway v1 upstream stream interrupted mid-flight baseUrl={}", baseUrl, ex);
                Long[] tokens = scanTailUsage(tail);
                return StreamAttempt.started(tokens[0], tokens[1], true);
            }
            log.warn("gateway v1 upstream stream connect failed baseUrl={}", baseUrl, ex);
            return StreamAttempt.connectFailed();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void appendTail(StringBuilder tail, String chunk) {
        tail.append(chunk);
        if (tail.length() > STREAM_TAIL_CHARS) {
            tail.delete(0, tail.length() - STREAM_TAIL_CHARS);
        }
    }

    /**
     * 从流尾文本里取最后一组 prompt/completion tokens。OpenAI 形状的流里这两个
     * 字段只出现在 usage 对象（尾帧），按字段名扫尾段足够；扫不到返回 {null, null}。
     */
    private static Long[] scanTailUsage(CharSequence tail) {
        return new Long[] {lastLong(PROMPT_TOKENS_PATTERN, tail), lastLong(COMPLETION_TOKENS_PATTERN, tail)};
    }

    private static Long lastLong(Pattern pattern, CharSequence text) {
        Matcher matcher = pattern.matcher(text);
        Long value = null;
        while (matcher.find()) {
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // 扫到的不是数字就不采信，保持 null。
            }
        }
        return value;
    }

    private static byte[] readAllOrEmpty(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (in) {
            return in.readAllBytes();
        }
    }

    private void writeFinalStreamFailure(HttpServletResponse response, StreamAttempt attempt) throws IOException {
        if (attempt.preStartStatus() == null) {
            writeJsonError(response, 502, "api_error", "upstream_unavailable", "Upstream connection failed");
            return;
        }
        response.setStatus(attempt.preStartStatus());
        response.setContentType(
                attempt.preStartContentType() != null ? attempt.preStartContentType() : MediaType.APPLICATION_JSON_VALUE);
        if (attempt.preStartErrorBody() != null && attempt.preStartErrorBody().length > 0) {
            response.getOutputStream().write(attempt.preStartErrorBody());
        }
    }

    private void writeJsonError(HttpServletResponse response, int status, String type, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(GatewayV1ErrorDto.of(message, type, code)));
    }
}
