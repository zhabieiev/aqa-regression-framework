package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionMcpServerStdioIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROCESS_CLEANUP_TIMEOUT_SECONDS = 2;
    private final List<Process> startedProcesses = new ArrayList<>();

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void cleansUpEveryStartedServerProcess() {
        for (Process process : startedProcesses) {
            terminateProcess(process);
            assertThat(process.isAlive()).as("server process %s must not remain alive", process.pid()).isFalse();
        }
    }

    @Test
    void servesDiscoveryToolsOverStdioAndTerminatesWhenTheClientDisconnects() throws Exception {
        Process process = startServer(createValidRoot());
        long processId = process.pid();
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                JsonNode toolsList = request(stdin, stdout, readerExecutor, 2, "tools/list", Map.of());
                assertToolList(toolsList);

                JsonNode features = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_FEATURES_TOOL_NAME, "arguments", Map.of("module", "commerce")));
                JsonNode scenarios = request(stdin, stdout, readerExecutor, 4, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME,
                        "arguments", Map.of("module", "commerce", "tags", "@smoke and @cart")));
                assertThat(features.path("result").path("isError").asBoolean()).isFalse();
                assertThat(features.path("result").path("structuredContent").path("data").path("features").size()).isEqualTo(1);
                assertThat(scenarios.path("result").path("isError").asBoolean()).isFalse();
                assertThat(scenarios.path("result").path("structuredContent").path("data").path("scenarios").size()).isEqualTo(1);
            }
            finally {
                readerExecutor.shutdownNow();
            }
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("server process must terminate after normal client EOF").isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(ProcessHandle.of(processId)).isEmpty();
        }
        finally {
            terminateProcess(process);
        }
    }

    @Test
    void returnsAStructuredErrorForMalformedGherkinWithoutPartialResults() throws Exception {
        Process process = startServer(createRootWithMalformedFeature());
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                JsonNode response = request(stdin, stdout, readerExecutor, 2, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_FEATURES_TOOL_NAME, "arguments", Map.of("module", "commerce")));
                assertThat(response.path("result").path("isError").asBoolean()).isTrue();
                assertThat(response.path("result").path("structuredContent").path("status").asText()).isEqualTo("error");
                assertThat(response.path("result").path("structuredContent").path("error").path("code").asText())
                        .isEqualTo("GHERKIN_PARSE_ERROR");
                assertThat(response.path("result").path("structuredContent").path("data").isMissingNode()).isTrue();
            }
            finally {
                readerExecutor.shutdownNow();
            }
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            terminateProcess(process);
        }
    }

    private void initialize(BufferedWriter stdin, BufferedReader stdout, ExecutorService readerExecutor) throws Exception {
        JsonNode initialize = request(stdin, stdout, readerExecutor, 1, "initialize", Map.of(
                "protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "stdio-integration-test", "version", "1.0.0")));
        assertThat(initialize.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(initialize.path("result").path("serverInfo").path("name").asText()).isEqualTo(RegressionMcpServer.SERVER_NAME);
        stdin.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "method", "notifications/initialized", "params", Map.of())));
        stdin.newLine();
        stdin.flush();
    }

    private void assertToolList(JsonNode toolsList) {
        assertThat(toolsList.path("result").path("tools").size()).isEqualTo(4);
        for (JsonNode tool : toolsList.path("result").path("tools")) {
            assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
            assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
            assertThat(tool.path("annotations").path("destructiveHint").asBoolean()).isFalse();
            assertThat(tool.path("annotations").path("idempotentHint").asBoolean()).isTrue();
            assertThat(tool.path("annotations").path("openWorldHint").asBoolean()).isFalse();
        }
        assertThat(toolsList.path("result").path("tools").get(2).path("name").asText())
                .isEqualTo(RegressionMcpServer.LIST_FEATURES_TOOL_NAME);
        assertThat(toolsList.path("result").path("tools").get(3).path("name").asText())
                .isEqualTo(RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME);
    }

    private JsonNode request(BufferedWriter stdin, BufferedReader stdout, ExecutorService readerExecutor, int id,
            String method, Map<String, Object> params) throws Exception {
        stdin.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params)));
        stdin.newLine();
        stdin.flush();
        Future<String> response = readerExecutor.submit(stdout::readLine);
        String line = response.get(10, TimeUnit.SECONDS);
        assertThat(line).as("stdout must contain only an MCP JSON-RPC response").isNotBlank();
        return JSON.readTree(line);
    }

    private void terminateProcess(Process process) {
        close(process.getOutputStream());
        awaitTermination(process);
        if (process.isAlive()) {
            process.destroy();
            awaitTermination(process);
        }
        if (process.isAlive()) {
            process.destroyForcibly();
            awaitTermination(process);
        }
        close(process.getInputStream());
        close(process.getErrorStream());
    }

    private boolean awaitTermination(Process process) {
        try {
            return process.waitFor(PROCESS_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    private void close(AutoCloseable closeable) {
        try {
            closeable.close();
        }
        catch (Exception ignored) {
            // Cleanup must continue through every remaining process resource.
        }
    }

    private Process startServer(Path root) throws IOException {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", testClasspath, RegressionMcpServer.class.getName());
        builder.environment().put(RepositoryRootResolver.ENVIRONMENT_VARIABLE, root.toString());
        Process process = builder.start();
        startedProcesses.add(process);
        return process;
    }

    private Path createValidRoot() throws Exception {
        Path root = createRoot();
        Path feature = root.resolve("commerce/src/test/resources/features/cart.feature");
        Files.createDirectories(feature.getParent());
        Files.writeString(feature, "@smoke @cart\nFeature: cart\n  Scenario: add\n    Given x\n");
        return root;
    }

    private Path createRootWithMalformedFeature() throws Exception {
        Path root = createRoot();
        Path feature = root.resolve("commerce/src/test/resources/features/broken.feature");
        Files.createDirectories(feature.getParent());
        Files.writeString(feature, "Feature: broken\n  Scenario: bad\n    Given x\n    \"\"\"\n    unclosed");
        return root;
    }

    private Path createRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules><module>commerce</module></modules></project>");
        Path module = temporaryDirectory.resolve("commerce");
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        return temporaryDirectory;
    }
}
