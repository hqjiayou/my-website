package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * 第 3 层契约测试：真实 Python agent-services 进程 + {@link HttpAgentClient}。
 *
 * <p>默认在 CI 启用（{@code AGENT_PYTHON_CONTRACT_IT=true}）。本地无 Python/uv 时自动跳过。
 */
@Tag("agent-python-contract")
@EnabledIf("com.mywebsite.agent.client.HttpAgentClientPythonContractIT#isEnabled")
class HttpAgentClientPythonContractIT {

    private static final String TOKEN = "contract-test-token";

    private static Process pythonProcess;
    private static int port;
    private static HttpAgentClient client;

    @BeforeAll
    static void startPythonAgent() throws Exception {
        if (!isEnabled()) {
            return;
        }
        port = findFreePort();
        Path agentServicesDir = locateAgentServicesDir();
        List<String> command = buildStartCommand(agentServicesDir, port);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(agentServicesDir.toFile());
        builder.environment().put("AGENT_INTERNAL_SERVICE_TOKEN", TOKEN);
        builder.environment().put("AGENT_CONTRACT_USE_TEST_MODEL", "true");
        builder.environment().put("PORT", String.valueOf(port));
        builder.redirectErrorStream(true);
        pythonProcess = builder.start();
        drainProcessOutputAsync(pythonProcess);
        waitForHealth(port, Duration.ofSeconds(45));

        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setInternalServiceToken(TOKEN);
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(10000);
        properties.setRetryMaxAttempts(0);
        client = new HttpAgentClient(properties, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @AfterAll
    static void stopPythonAgent() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            try {
                pythonProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                pythonProcess.destroyForcibly();
            }
        }
    }

    static boolean isEnabled() {
        if (!"true".equalsIgnoreCase(System.getenv("AGENT_PYTHON_CONTRACT_IT"))) {
            return false;
        }
        return locateAgentServicesDirOptional().isPresent() && commandExists("uv");
    }

    @Test
    void healthReportsLiveWithContractTestModel() {
        AgentPingResponse ping = client.ping();
        assertThat(ping.providerStatus()).isEqualTo("LIVE");
        assertThat(ping.message()).containsIgnoringCase("agent");
    }

    @Test
    void chatBlockedWhenTokenInvalid() {
        AgentProperties wrong = new AgentProperties();
        wrong.setBaseUrl("http://127.0.0.1:" + port);
        wrong.setInternalServiceToken("wrong-token");
        HttpAgentClient wrongClient =
                new HttpAgentClient(wrong, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThatThrownBy(() -> wrongClient.chat(new AgentChatRequest("1", "hello")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }

    @Test
    void chatLiveSuccessAgainstRealPythonService() throws Exception {
        String debug = debugChatPost();
        assertThat(debug).startsWith("status=200");

        AgentChatResponse response = client.chat(new AgentChatRequest("42", "hello"));
        assertThat(response.reply()).isEqualTo("contract-test-reply");
        assertThat(response.providerStatus()).isEqualTo("LIVE");
    }

    @Test
    void streamEndpointEmitsApplicationEvents() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + "/internal/v1/chat/stream").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Internal-Service-Token", TOKEN);
        byte[] body = "{\"siteId\":\"1\",\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(body);

        assertThat(connection.getResponseCode()).isEqualTo(200);
        String streamBody;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            streamBody = sb.toString();
        }
        assertThat(streamBody).contains("event: START");
        assertThat(streamBody).contains("event: DONE");
        assertThat(streamBody).contains("contract-test-reply");
    }

    @Test
    void chatStreamClientForwardsEventsAgainstRealPythonService() {
        List<String> events = new ArrayList<>();
        client.chatStream(
                new AgentChatRequest("42", "hello"), (event, data) -> events.add(event + "|" + data));

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).startsWith("START|");
        assertThat(events.get(events.size() - 1)).startsWith("DONE|");
        assertThat(String.join("\n", events)).contains("contract-test-reply");
    }

    private static String debugChatPost() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + "/internal/v1/chat").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Internal-Service-Token", TOKEN);
        byte[] body = "{\"siteId\":\"42\",\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(body);
        int status = connection.getResponseCode();
        var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return "status=" + status + " body=" + text;
    }

    private static void waitForHealth(int port, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        URL url = new URL("http://127.0.0.1:" + port + "/health");
        while (System.nanoTime() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // not ready yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Python agent-services did not become healthy on port " + port);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static Path locateAgentServicesDir() {
        return locateAgentServicesDirOptional()
                .orElseThrow(() -> new IllegalStateException("agent-services directory not found"));
    }

    private static java.util.Optional<Path> locateAgentServicesDirOptional() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++) {
            Path byRepoLayout = cursor.resolve("services/agent-services");
            if (isAgentServicesRoot(byRepoLayout)) {
                return java.util.Optional.of(byRepoLayout.normalize());
            }
            Path sibling = cursor.resolve("agent-services");
            if (isAgentServicesRoot(sibling)) {
                return java.util.Optional.of(sibling.normalize());
            }
            cursor = cursor.getParent();
        }
        return java.util.Optional.empty();
    }

    private static boolean isAgentServicesRoot(Path candidate) {
        return Files.isDirectory(candidate) && Files.exists(candidate.resolve("pyproject.toml"));
    }

    private static List<String> buildStartCommand(Path agentServicesDir, int port) {
        return List.of(
                "uv",
                "run",
                "uvicorn",
                "agent_services.app:create_app",
                "--factory",
                "--host",
                "127.0.0.1",
                "--port",
                String.valueOf(port));
    }

    private static boolean commandExists(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void drainProcessOutputAsync(Process process) {
        Thread t = new Thread(
                () -> {
                    try (BufferedReader reader =
                            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        while (reader.readLine() != null) {
                            // discard to avoid blocking
                        }
                    } catch (IOException ignored) {
                        // process ended
                    }
                },
                "agent-python-contract-log");
        t.setDaemon(true);
        t.start();
    }
}
