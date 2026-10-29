package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminProxyServicesMessageKeys;
import com.mywebsite.admin.properties.AdminProxyServicesProperties;
import com.mywebsite.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AdminProxyServicesServiceImplTest {

    @Test
    void statusListsAccountsWhenProcessAndManagementRespond() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.createContext("/v8/management/credentials", exchange -> {
            byte[] body =
                    """
                    {"files":[{"name":"codex.json","provider":"codex","email":"a@b.c","disabled":false,"unavailable":false,"success":3,"failed":1,"quota":{"signals":{"x-codex-primary-used-percent":"58"}},"model_quotas":{"gpt-6-sol":{"signals":{"x-codex-primary-used-percent":"40"}}}}]}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AdminProxyServicesProperties properties = new AdminProxyServicesProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setManagementKey("local-dev-proxy-management-key");
            var status = new AdminProxyServicesServiceImpl(properties, new ObjectMapper()).status();
            assertThat(status.name()).isEqualTo("proxy-services");
            assertThat(status.reachable()).isTrue();
            assertThat(status.managementConfigured()).isTrue();
            assertThat(status.accounts()).hasSize(1);
            assertThat(status.accounts().get(0).provider()).isEqualTo("codex");
            assertThat(status.accounts().get(0).email()).isEqualTo("a@b.c");
            assertThat(status.accounts().get(0).success()).isEqualTo(3);
            assertThat(status.accounts().get(0).failed()).isEqualTo(1);
            assertThat(status.accounts().get(0).usedPercent()).isEqualTo(58);
            assertThat(status.accounts().get(0).modelUsedPercent()).containsEntry("gpt-6-sol", 40);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void statusIsUnreachableWhenNothingListens() {
        AdminProxyServicesProperties properties = new AdminProxyServicesProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setConnectTimeoutMs(200);
        properties.setReadTimeoutMs(200);
        properties.setManagementKey("local-dev-proxy-management-key");
        var status = new AdminProxyServicesServiceImpl(properties, new ObjectMapper()).status();
        assertThat(status.reachable()).isFalse();
        assertThat(status.accounts()).isEmpty();
    }

    @Test
    void apiKeysRoundTripThroughManagementConfig() throws Exception {
        AtomicReference<List<String>> stored = new AtomicReference<>(new ArrayList<>(List.of("client-key-1")));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/config/access/api-keys", exchange -> {
            if (!"Bearer local-dev-proxy-management-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                stored.set(new ArrayList<>(new ObjectMapper().readerForListOf(String.class).readValue(body)));
                byte[] ok = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                exchange.getResponseBody().write(ok);
                exchange.close();
                return;
            }
            writeJson(exchange, new ObjectMapper().writeValueAsString(stored.get()));
        });
        server.start();
        try {
            AdminProxyServicesServiceImpl service = service(server.getAddress().getPort());
            assertThat(service.listApiKeys().apiKeys()).containsExactly("client-key-1");
            String created = service.createApiKey("second-key");
            assertThat(created).isEqualTo("second-key");
            assertThat(service.listApiKeys().apiKeys()).containsExactly("client-key-1", "second-key");
            service.deleteApiKey("client-key-1");
            assertThat(stored.get()).containsExactly("second-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void createApiKeyRejectsDuplicate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/config/access/api-keys", exchange -> writeJson(exchange, "[\"client-key-1\"]"));
        server.start();
        try {
            AdminProxyServicesServiceImpl service = service(server.getAddress().getPort());
            BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.createApiKey("client-key-1"));
            assertThat(ex.getMessageKey()).isEqualTo(AdminProxyServicesMessageKeys.API_KEY_DUPLICATE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void startLoginReturnsAuthUrlForCodexAndRejectsOtherProviders() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/oauth/auth-url", exchange -> {
            path.set(exchange.getRequestURI().getRawQuery());
            if (!"Bearer local-dev-proxy-management-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            writeJson(
                    exchange,
                    "{\"status\":\"ok\",\"url\":\"https://auth.example/authorize\",\"state\":\"0123456789abcdef0123456789abcdef\"}");
        });
        server.start();
        try {
            AdminProxyServicesServiceImpl service = service(server.getAddress().getPort());
            var started = service.startLogin(" Codex ");
            assertThat(started.provider()).isEqualTo("codex");
            assertThat(started.url()).isEqualTo("https://auth.example/authorize");
            assertThat(started.state()).isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(path.get()).isEqualTo("provider=codex&is_webui=true");
            assertThat(service.startLogin("antigravity").provider()).isEqualTo("antigravity");
            assertThat(path.get()).isEqualTo("provider=antigravity&is_webui=true");
            assertThat(service.startLogin("meta").provider()).isEqualTo("meta");
            BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.startLogin("gemini"));
            assertThat(ex.getMessageKey()).isEqualTo(AdminProxyServicesMessageKeys.LOGIN_PROVIDER_INVALID);
            BusinessException cursor = catchThrowableOfType(BusinessException.class, () -> service.startLogin("cursor"));
            assertThat(cursor.getMessageKey()).isEqualTo(AdminProxyServicesMessageKeys.LOGIN_PROVIDER_INVALID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loginStatusReadsWaitOkAndError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/oauth/status", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query != null && query.contains("state=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                    ? "{\"status\":\"error\",\"error\":\"callback failed\"}"
                    : "{\"status\":\"wait\"}";
            writeJson(exchange, body);
        });
        server.start();
        try {
            AdminProxyServicesServiceImpl service = service(server.getAddress().getPort());
            assertThat(service.loginStatus("0123456789abcdef0123456789abcdef").status()).isEqualTo("wait");
            assertThat(service.loginStatus("meta-1793281722123456789").status()).isEqualTo("wait");
            var failed = service.loginStatus("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            assertThat(failed.status()).isEqualTo("error");
            assertThat(failed.error()).isEqualTo("callback failed");
            BusinessException ex = catchThrowableOfType(BusinessException.class, () -> service.loginStatus("nope"));
            assertThat(ex.getMessageKey()).isEqualTo(AdminProxyServicesMessageKeys.LOGIN_STATE_INVALID);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usageListsModelsWithTheClientKey() throws Exception {
        AtomicReference<String> modelsAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/config/access/api-keys", exchange -> writeJson(exchange, "[\"client-key-1\"]"));
        server.createContext("/v8/management/credentials/models", exchange -> {
            modelsAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, "{\"models\":[{\"id\":\"gpt-6-sol\"},{\"id\":\"\"}]}");
        });
        server.createContext(
                "/v8/management/credentials",
                exchange -> writeJson(
                        exchange,
                        "{\"files\":[{\"name\":\"codex.json\",\"provider\":\"codex\",\"disabled\":false,\"unavailable\":false}]}"));
        server.start();
        try {
            var usage = service(server.getAddress().getPort()).usage();
            assertThat(modelsAuth.get()).isEqualTo("Bearer local-dev-proxy-management-key");
            assertThat(usage.callable()).isTrue();
            assertThat(usage.apiBaseUrl()).isEqualTo("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            assertThat(usage.accounts()).hasSize(1);
            assertThat(usage.accounts().get(0).name()).isEqualTo("codex.json");
            assertThat(usage.accounts().get(0).provider()).isEqualTo("codex");
            assertThat(usage.accounts().get(0).modelIds()).containsExactly("gpt-6-sol");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usageIsNotCallableWithoutAClientKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/management/config/access/api-keys", exchange -> writeJson(exchange, "[]"));
        server.start();
        try {
            var usage = service(server.getAddress().getPort()).usage();
            assertThat(usage.callable()).isFalse();
            assertThat(usage.accounts()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    private static AdminProxyServicesServiceImpl service(int port) {
        AdminProxyServicesProperties properties = new AdminProxyServicesProperties();
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setManagementKey("local-dev-proxy-management-key");
        return new AdminProxyServicesServiceImpl(properties, new ObjectMapper());
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
