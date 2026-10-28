package com.mywebsite.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentPingResponse;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentSkillSummary;
import com.mywebsite.agent.dto.AgentTodoItem;
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
import java.util.Map;
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
        // §22：成功体带回用量（TestModel 至少 1 次 model request）
        assertThat(response.runMeta().usage().requests()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void chatWithAttachmentSucceedsAgainstRealPythonService() {
        AgentChatResponse response = client.chat(new AgentChatRequest(
                "42",
                "describe",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new AgentAttachmentDto(
                        "image/png",
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))));
        assertThat(response.reply()).isEqualTo("contract-test-reply");
        assertThat(response.providerStatus()).isEqualTo("LIVE");
    }

    @Test
    void chatRoundTripsTodosAndReportsSkillsApplied() {
        AgentTodoItem existing = new AgentTodoItem("t1", "Step 1", "IN_PROGRESS");
        AgentChatResponse response = client.chat(new AgentChatRequest(
                "42", "hello", null, null, null, null, List.of("platform-smoke"), List.of(existing)));

        // TestModel 会调用全部工具（可能新增条目），但上轮条目必须原样回到快照中
        assertThat(response.todos()).contains(existing);
        assertThat(response.skillsApplied()).contains("platform-smoke");
        assertThat(response.runMeta().tools()).isNotEmpty();
        assertThat(response.runMeta().tools()).allSatisfy(tool -> assertThat(tool.name()).isNotBlank());
    }

    @Test
    void runtimeStatusReportsLimitsAgainstRealPythonService() {
        AgentRuntimeStatus status = client.runtimeStatus();
        assertThat(status.providerStatus()).isEqualTo("LIVE");
        assertThat(status.limits().maxRequests()).isEqualTo(10);
        assertThat(status.limits().maxToolCalls()).isEqualTo(20);
        assertThat(status.limits().toolTimeoutSeconds()).isEqualTo(30.0);
        assertThat(status.limits().historyMaxChars()).isEqualTo(24000);
        assertThat(status.mcpStatus()).isEqualTo("DISABLED");
    }

    @Test
    void listSkillsReturnsBuiltInCatalog() {
        List<AgentSkillSummary> skills = client.listSkills();
        assertThat(skills).extracting(AgentSkillSummary::id).contains("orchestration", "platform-smoke");
    }

    @Test
    void oversizedTodosMapToBadRequest() {
        List<AgentTodoItem> tooMany = new ArrayList<>();
        for (int i = 0; i <= AgentTodoItem.MAX_ITEMS; i++) {
            tooMany.add(new AgentTodoItem("t" + i, "x", "PENDING"));
        }
        assertThatThrownBy(() -> client.chat(
                        new AgentChatRequest("42", "hello", null, null, null, null, null, tooMany)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    void streamEndpointEmitsApplicationEvents() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + "/internal/v1/chat/stream").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Internal-Service-Token", TOKEN);
        byte[] body = "{\"agentId\":\"1\",\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
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
        String joined = String.join("\n", events);
        assertThat(joined).contains("contract-test-reply");
        assertThat(joined).contains("\"usage\"");
        assertThat(joined).contains("\"tools\"");
    }

    @Test
    void budgetExceededMapsToAgentBudgetExceededAgainstRealPythonService() throws Exception {
        // 独立进程：默认 maxRequests=10 不会触发；request_limit=1 + TestModel 调工具 → 第二轮 model 请求超限
        try (EphemeralAgentProcess proc = EphemeralAgentProcess.start(Map.of("AGENT_MAX_REQUESTS", "1"))) {
            assertThatThrownBy(() -> proc.client().chat(new AgentChatRequest("42", "hello")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.AGENT_BUDGET_EXCEEDED.getCode());
        }
    }

    @Test
    void mcpUnavailableMapsToExternalDependencyBlockedAgainstRealPythonService() throws Exception {
        String mcpJson = "[{\"name\":\"dead\",\"url\":\"http://127.0.0.1:1/mcp\",\"enabled\":true}]";
        try (EphemeralAgentProcess proc = EphemeralAgentProcess.start(Map.of("MCP_SERVERS_JSON", mcpJson))) {
            assertThat(proc.client().runtimeStatus().mcpStatus()).isEqualTo("CONFIGURED");
            assertThatThrownBy(() -> proc.client().chat(new AgentChatRequest("42", "hello")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode());
        }
    }

    private static String debugChatPost() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + "/internal/v1/chat").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Internal-Service-Token", TOKEN);
        byte[] body = "{\"agentId\":\"42\",\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
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

    /** 短生命周期 agent-services：用于覆盖与默认进程配置冲突的契约（预算 / MCP）。 */
    private static final class EphemeralAgentProcess implements AutoCloseable {
        private final Process process;
        private final HttpAgentClient client;

        private EphemeralAgentProcess(Process process, HttpAgentClient client) {
            this.process = process;
            this.client = client;
        }

        static EphemeralAgentProcess start(Map<String, String> extraEnv) throws Exception {
            int ephemeralPort = findFreePort();
            Path agentServicesDir = locateAgentServicesDir();
            ProcessBuilder builder = new ProcessBuilder(buildStartCommand(agentServicesDir, ephemeralPort));
            builder.directory(agentServicesDir.toFile());
            builder.environment().put("AGENT_INTERNAL_SERVICE_TOKEN", TOKEN);
            builder.environment().put("AGENT_CONTRACT_USE_TEST_MODEL", "true");
            builder.environment().put("PORT", String.valueOf(ephemeralPort));
            builder.environment().putAll(extraEnv);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            drainProcessOutputAsync(process);
            waitForHealth(ephemeralPort, Duration.ofSeconds(45));

            AgentProperties properties = new AgentProperties();
            properties.setBaseUrl("http://127.0.0.1:" + ephemeralPort);
            properties.setInternalServiceToken(TOKEN);
            properties.setConnectTimeoutMs(3000);
            properties.setReadTimeoutMs(15000);
            properties.setRetryMaxAttempts(0);
            return new EphemeralAgentProcess(
                    process, new HttpAgentClient(properties, new com.fasterxml.jackson.databind.ObjectMapper()));
        }

        HttpAgentClient client() {
            return client;
        }

        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }
    }
}
