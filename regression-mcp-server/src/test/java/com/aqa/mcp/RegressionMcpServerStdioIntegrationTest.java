package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionMcpServerStdioIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROCESS_CLEANUP_TIMEOUT_SECONDS = 2;

    @TempDir
    Path temporaryDirectory;

    @Test
    void servesTheFrameworkOverviewOverStdioAndTerminatesWhenTheClientDisconnects() throws Exception {
        Path root = createValidRoot();
        Process process = startServer(root);
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        try {
            try (BufferedWriter stdin = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                JsonNode initialize = request(stdin, stdout, readerExecutor, 1, "initialize", Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "stdio-integration-test", "version", "1.0.0")));

                assertThat(initialize.path("jsonrpc").asText()).isEqualTo("2.0");
                assertThat(initialize.path("result").path("serverInfo").path("name").asText())
                        .isEqualTo(RegressionMcpServer.SERVER_NAME);
                assertThat(initialize.path("result").path("instructions").asText())
                        .contains("local, read-only framework inspection server");
                assertThat(initialize.path("result").path("capabilities").has("tools")).isTrue();
                assertThat(initialize.path("result").path("capabilities").has("resources")).isFalse();
                assertThat(initialize.path("result").path("capabilities").has("prompts")).isFalse();

                stdin.write(JSON.writeValueAsString(Map.of(
                        "jsonrpc", "2.0",
                        "method", "notifications/initialized",
                        "params", Map.of())));
                stdin.newLine();
                stdin.flush();

                JsonNode toolsList = request(stdin, stdout, readerExecutor, 2, "tools/list", Map.of());
                JsonNode tool = toolsList.path("result").path("tools").get(0);
                JsonNode moduleListTool = toolsList.path("result").path("tools").get(1);
                JsonNode annotations = tool.path("annotations");
                JsonNode moduleListAnnotations = moduleListTool.path("annotations");
                assertThat(toolsList.path("result").path("tools").size()).isEqualTo(2);
                assertThat(tool.path("name").asText()).isEqualTo(RegressionMcpServer.OVERVIEW_TOOL_NAME);
                assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
                assertThat(tool.path("outputSchema").path("required").size()).isEqualTo(2);
                assertThat(annotations.has("readOnlyHint")).isTrue();
                assertThat(annotations.path("readOnlyHint").asBoolean()).isTrue();
                assertThat(annotations.has("destructiveHint")).isTrue();
                assertThat(annotations.path("destructiveHint").asBoolean()).isFalse();
                assertThat(annotations.has("idempotentHint")).isTrue();
                assertThat(annotations.path("idempotentHint").asBoolean()).isTrue();
                assertThat(annotations.has("openWorldHint")).isTrue();
                assertThat(annotations.path("openWorldHint").asBoolean()).isFalse();
                assertThat(moduleListTool.path("name").asText()).isEqualTo(RegressionMcpServer.LIST_MODULES_TOOL_NAME);
                assertThat(moduleListTool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
                assertThat(moduleListTool.path("outputSchema").has("oneOf")).isTrue();
                assertThat(moduleListAnnotations.path("readOnlyHint").asBoolean()).isTrue();
                assertThat(moduleListAnnotations.path("destructiveHint").asBoolean()).isFalse();
                assertThat(moduleListAnnotations.path("idempotentHint").asBoolean()).isTrue();
                assertThat(moduleListAnnotations.path("openWorldHint").asBoolean()).isFalse();

                JsonNode firstCall = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of(
                        "name", RegressionMcpServer.OVERVIEW_TOOL_NAME,
                        "arguments", Map.of()));
                JsonNode secondCall = request(stdin, stdout, readerExecutor, 4, "tools/call", Map.of(
                        "name", RegressionMcpServer.OVERVIEW_TOOL_NAME,
                        "arguments", Map.of()));
                JsonNode invalidCall = request(stdin, stdout, readerExecutor, 5, "tools/call", Map.of(
                        "name", RegressionMcpServer.OVERVIEW_TOOL_NAME,
                        "arguments", Map.of("arbitraryPath", "D:/outside-the-root")));

                JsonNode moduleListCall = request(stdin, stdout, readerExecutor, 6, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_MODULES_TOOL_NAME,
                        "arguments", Map.of()));

                assertThat(firstCall.path("jsonrpc").asText()).isEqualTo("2.0");
                assertThat(firstCall.path("result").path("isError").asBoolean()).isFalse();
                assertThat(firstCall.path("result").path("structuredContent"))
                        .isEqualTo(secondCall.path("result").path("structuredContent"));
                assertThat(firstCall.path("result").path("structuredContent"))
                        .isEqualTo(JSON.readTree(JSON.writeValueAsString(Map.of(
                                "status", "ok",
                                "data", Map.of(
                                        "name", "regression",
                                        "root", root.toRealPath().toString().replace('\\', '/'),
                                        "javaVersion", "21",
                                        "buildTool", "Maven",
                                        "availability", "AVAILABLE")))));
                assertThat(invalidCall.path("result").path("isError").asBoolean()).isTrue();
                assertThat(moduleListCall.path("result").path("isError").asBoolean()).isFalse();
                assertThat(moduleListCall.path("result").path("structuredContent"))
                        .isEqualTo(JSON.readTree(JSON.writeValueAsString(Map.of(
                                "status", "ok", "data", Map.of("modules", java.util.List.of())))));
            }

            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("server process must terminate after STDIO closes")
                    .isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8))
                    .contains("input validation failed");
        }
        finally {
            readerExecutor.shutdownNow();
            terminateProcess(process);
        }
    }

    private JsonNode request(BufferedWriter stdin, BufferedReader stdout, ExecutorService readerExecutor, int id,
            String method, Map<String, Object> params) throws Exception {
        stdin.write(JSON.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params)));
        stdin.newLine();
        stdin.flush();

        Future<String> response = readerExecutor.submit(stdout::readLine);
        String line = response.get(10, TimeUnit.SECONDS);
        assertThat(line).as("stdout must contain an MCP JSON-RPC response").isNotBlank();
        return JSON.readTree(line);
    }

    private void terminateProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }

        process.destroy();
        if (awaitTermination(process)) {
            return;
        }

        process.destroyForcibly();
        awaitTermination(process);
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

    private Process startServer(Path root) throws Exception {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp",
                testClasspath,
                RegressionMcpServer.class.getName());
        processBuilder.environment().put(RepositoryRootResolver.ENVIRONMENT_VARIABLE, root.toString());
        return processBuilder.start();
    }

    private Path createValidRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project/>");
        return temporaryDirectory;
    }
}
