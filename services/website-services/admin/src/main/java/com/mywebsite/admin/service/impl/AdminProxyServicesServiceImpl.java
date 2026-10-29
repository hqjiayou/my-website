package com.mywebsite.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminProxyServicesMessageKeys;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesAccountDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesApiKeysDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesLoginStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesPublishedAccountDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesUsageDto;
import com.mywebsite.admin.properties.AdminProxyServicesProperties;
import com.mywebsite.admin.service.AdminProxyServicesService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminProxyServicesServiceImpl implements AdminProxyServicesService {

    static final String NAME = "proxy-services";

    private static final String API_KEYS_PATH = "/v8/management/config/access/api-keys";

    private static final Set<String> LOGIN_PROVIDERS = Set.of("codex", "claude", "antigravity", "meta");

    private static final Pattern OAUTH_STATE = Pattern.compile("[A-Za-z0-9_-]{16,80}");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AdminProxyServicesProperties properties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    @Autowired
    public AdminProxyServicesServiceImpl(AdminProxyServicesProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1)))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    AdminProxyServicesServiceImpl(
            AdminProxyServicesProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public AdminProxyServicesStatusDto status() {
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        boolean managementConfigured = StringUtils.hasText(properties.getManagementKey());
        if (baseUrl == null) {
            return new AdminProxyServicesStatusDto(NAME, "", false, managementConfigured, List.of());
        }
        boolean reachable = probe(baseUrl);
        List<AdminProxyServicesAccountDto> accounts =
                reachable && managementConfigured ? accounts(baseUrl) : List.of();
        return new AdminProxyServicesStatusDto(NAME, baseUrl, reachable, managementConfigured, accounts);
    }

    @Override
    public AdminProxyServicesApiKeysDto listApiKeys() {
        return new AdminProxyServicesApiKeysDto(List.copyOf(readKeys(requireBaseUrl())));
    }

    @Override
    public String createApiKey(String apiKey) {
        String baseUrl = requireBaseUrl();
        List<String> keys = new ArrayList<>(readKeys(baseUrl));
        String created = StringUtils.hasText(apiKey) ? requireApiKey(apiKey) : generateApiKey();
        if (keys.contains(created)) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminProxyServicesMessageKeys.API_KEY_DUPLICATE);
        }
        keys.add(created);
        writeKeys(baseUrl, keys);
        return created;
    }

    @Override
    public void deleteApiKey(String apiKey) {
        String baseUrl = requireBaseUrl();
        String target = requireApiKey(apiKey);
        List<String> keys = new ArrayList<>(readKeys(baseUrl));
        if (!keys.remove(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminProxyServicesMessageKeys.API_KEY_NOT_FOUND);
        }
        writeKeys(baseUrl, keys);
    }

    @Override
    public AdminProxyServicesLoginDto startLogin(String provider) {
        String normalized = normalizeProvider(provider);
        String baseUrl = requireBaseUrl();
        HttpResponse<String> response = send(
                "GET",
                baseUrl + "/v8/management/oauth/auth-url?provider=" + normalized + "&is_webui=true",
                null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw loginFailed();
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String authUrl = text(node, "url");
            String state = text(node, "state");
            if ((!authUrl.startsWith("https://") && !authUrl.startsWith("http://")) || !OAUTH_STATE.matcher(state).matches()) {
                throw loginFailed();
            }
            return new AdminProxyServicesLoginDto(normalized, authUrl, state);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw loginFailed();
        }
    }

    @Override
    public AdminProxyServicesLoginStatusDto loginStatus(String state) {
        if (!StringUtils.hasText(state) || !OAUTH_STATE.matcher(state.trim()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminProxyServicesMessageKeys.LOGIN_STATE_INVALID);
        }
        String baseUrl = requireBaseUrl();
        HttpResponse<String> response =
                send("GET", baseUrl + "/v8/management/oauth/status?state=" + state.trim(), null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable();
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String status = text(node, "status");
            if (!status.equals("ok") && !status.equals("wait") && !status.equals("error")) {
                status = "error";
            }
            String error = status.equals("error") ? shorten(text(node, "error")) : "";
            return new AdminProxyServicesLoginStatusDto(status, error);
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    @Override
    public AdminProxyServicesUsageDto usage() {
        String baseUrl = requireBaseUrl();
        String apiBaseUrl = baseUrl + "/v1";
        List<AdminProxyServicesPublishedAccountDto> published = new ArrayList<>();
        boolean hasModels = false;
        for (AdminProxyServicesAccountDto account : accounts(baseUrl)) {
            List<String> modelIds =
                    account.disabled() || account.unavailable() ? List.of() : modelsFor(baseUrl, account.name());
            if (!modelIds.isEmpty()) {
                hasModels = true;
            }
            published.add(new AdminProxyServicesPublishedAccountDto(
                    account.name(), account.provider(), account.disabled(), account.unavailable(), modelIds));
        }
        boolean callable = hasModels && !readKeys(baseUrl).isEmpty();
        return new AdminProxyServicesUsageDto(apiBaseUrl, callable, List.copyOf(published));
    }

    private List<String> modelsFor(String baseUrl, String name) {
        try {
            String url = baseUrl
                    + "/v8/management/credentials/models?name="
                    + URLEncoder.encode(name, StandardCharsets.UTF_8);
            HttpResponse<String> response = send("GET", url, null);
            if (response.statusCode() != 200 || !StringUtils.hasText(response.body())) {
                return List.of();
            }
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            if (!models.isArray()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode item : models) {
                String id = text(item, "id");
                if (StringUtils.hasText(id)) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminProxyServicesMessageKeys.LOGIN_PROVIDER_INVALID);
        }
        String value = provider.trim().toLowerCase(Locale.ROOT);
        if (!LOGIN_PROVIDERS.contains(value)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminProxyServicesMessageKeys.LOGIN_PROVIDER_INVALID);
        }
        return value;
    }

    private static BusinessException loginFailed() {
        return new BusinessException(
                ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode(), AdminProxyServicesMessageKeys.LOGIN_FAILED);
    }

    private static String shorten(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }

    private boolean probe(String baseUrl) {
        try {
            HttpResponse<Void> response = httpClient.send(
                    request(baseUrl + "/v1/models", null).GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() > 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private List<AdminProxyServicesAccountDto> accounts(String baseUrl) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request(baseUrl + "/v8/management/credentials", properties.getManagementKey().trim())
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            JsonNode files = objectMapper.readTree(response.body()).path("files");
            if (!files.isArray()) {
                return List.of();
            }
            List<AdminProxyServicesAccountDto> accounts = new ArrayList<>();
            for (JsonNode file : files) {
                String name = text(file, "name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                accounts.add(new AdminProxyServicesAccountDto(
                        name,
                        text(file, "provider"),
                        file.path("disabled").asBoolean(false),
                        file.path("unavailable").asBoolean(false),
                        blankToNull(text(file, "email")),
                        file.path("success").asLong(0),
                        file.path("failed").asLong(0),
                        usedPercent(file.path("quota").path("signals")),
                        modelUsedPercent(file.path("model_quotas"))));
            }
            return List.copyOf(accounts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String requireBaseUrl() {
        if (!StringUtils.hasText(properties.getManagementKey())) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode(), AdminProxyServicesMessageKeys.MANAGEMENT_KEY_MISSING);
        }
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        if (baseUrl == null) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode(), AdminProxyServicesMessageKeys.UNAVAILABLE);
        }
        return baseUrl;
    }

    private List<String> readKeys(String baseUrl) {
        HttpResponse<String> response = send("GET", baseUrl + API_KEYS_PATH, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable();
        }
        try {
            JsonNode node = objectMapper.readTree(
                    StringUtils.hasText(response.body()) ? response.body() : "[]");
            JsonNode array = node.isArray() ? node : node.path("api-keys");
            if (!array.isArray()) {
                throw unavailable();
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode item : array) {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    keys.add(item.asText().trim());
                }
            }
            return keys;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private void writeKeys(String baseUrl, List<String> keys) {
        try {
            HttpResponse<String> response =
                    send("PUT", baseUrl + API_KEYS_PATH, objectMapper.writeValueAsString(keys));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private HttpResponse<String> send(String method, String url, String jsonBody) {
        return send(method, url, jsonBody, properties.getManagementKey().trim());
    }

    private HttpResponse<String> send(String method, String url, String jsonBody, String bearer) {
        try {
            HttpRequest.Builder builder = request(url, bearer);
            if (jsonBody == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode(), AdminProxyServicesMessageKeys.UNAVAILABLE);
    }

    private static String requireApiKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminProxyServicesMessageKeys.API_KEY_INVALID);
        }
        String value = raw.trim();
        if (value.length() < 8 || value.length() > 200 || value.chars().anyMatch(ch -> ch <= ' ' || ch > 126)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminProxyServicesMessageKeys.API_KEY_INVALID);
        }
        return value;
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "cpa-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private HttpRequest.Builder request(String url, String managementKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 1)));
        if (StringUtils.hasText(managementKey)) {
            builder.header("Authorization", "Bearer " + managementKey);
        }
        return builder;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /** 优先 primary 已用比例，否则取第一条 used-percent 信号。 */
    private static Integer usedPercent(JsonNode signals) {
        if (!signals.isObject()) {
            return null;
        }
        Integer fallback = null;
        Iterator<Map.Entry<String, JsonNode>> fields = signals.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.contains("used-percent") && !key.contains("used_percent")) {
                continue;
            }
            Integer parsed = parsePercent(entry.getValue().asText(""));
            if (parsed == null) {
                continue;
            }
            if (key.contains("primary")) {
                return parsed;
            }
            if (fallback == null) {
                fallback = parsed;
            }
        }
        return fallback;
    }

    private static Map<String, Integer> modelUsedPercent(JsonNode quotas) {
        if (!quotas.isObject()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        quotas.fields().forEachRemaining(entry -> {
            Integer percent = usedPercent(entry.getValue().path("signals"));
            if (percent != null && StringUtils.hasText(entry.getKey())) {
                result.put(entry.getKey(), percent);
            }
        });
        return Map.copyOf(result);
    }

    private static Integer parsePercent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || value > 100) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    static String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if ((!value.startsWith("http://") && !value.startsWith("https://")) || value.contains("@")) {
            return null;
        }
        return value;
    }
}
