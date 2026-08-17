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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionMcpServerStdioIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PROCESS_CLEANUP_TIMEOUT_SECONDS = 2;
    private static final long REQUEST_TIMEOUT_SECONDS = 10;
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
                assertNoApplicationChildProcesses(process);
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
            assertThat(process.waitFor(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
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
                JsonNode overview = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of(
                        "name", RegressionMcpServer.OVERVIEW_TOOL_NAME, "arguments", Map.of()));
                assertThat(overview.path("result").path("isError").asBoolean()).isFalse();
            }
            finally {
                readerExecutor.shutdownNow();
            }
            assertThat(process.waitFor(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            terminateProcess(process);
        }
    }

    @Test
    void returnsStructuredPomErrorsWithoutExposingFixturePathsAndRemainsUsable() throws Exception {
        Process process = startServer(createRootWithMalformedPom());
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                JsonNode error = request(stdin, stdout, readerExecutor, 2, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_MODULES_TOOL_NAME, "arguments", Map.of()));
                assertStructuredError(error, "POM_ERROR");
                assertThat(JSON.writeValueAsString(error)).doesNotContain(temporaryDirectory.toString())
                        .doesNotContain("Exception").doesNotContain(" at ");

                JsonNode overview = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of(
                        "name", RegressionMcpServer.OVERVIEW_TOOL_NAME, "arguments", Map.of()));
                assertThat(overview.path("result").path("isError").asBoolean()).isFalse();
            }
            finally {
                readerExecutor.shutdownNow();
            }
        }
        finally {
            terminateProcess(process);
        }
    }

    @Test
    void keepsTheReadOnlyFixtureUnchangedAfterCallsAndSurvivesInvalidTagExpressions() throws Exception {
        Path root = createValidRoot();
        Map<String, String> before = fixtureSnapshot(root);
        Process process = startServer(root);
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                assertNoApplicationChildProcesses(process);
                JsonNode error = request(stdin, stdout, readerExecutor, 2, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME,
                        "arguments", Map.of("module", "commerce", "tags", "@smoke and")));
                assertStructuredError(error, "INVALID_TAG_EXPRESSION");

                JsonNode modules = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_MODULES_TOOL_NAME, "arguments", Map.of()));
                JsonNode features = request(stdin, stdout, readerExecutor, 4, "tools/call", Map.of(
                        "name", RegressionMcpServer.LIST_FEATURES_TOOL_NAME, "arguments", Map.of("module", "commerce")));
                assertThat(modules.path("result").path("isError").asBoolean()).isFalse();
                assertThat(features.path("result").path("isError").asBoolean()).isFalse();
                assertNoApplicationChildProcesses(process);
            }
            finally {
                readerExecutor.shutdownNow();
            }
            assertThat(fixtureSnapshot(root)).isEqualTo(before);
        }
        finally {
            terminateProcess(process);
        }
    }

    @Test
    void servesControlledExecutionToolsOverStdioAndCleansRunOnEof() throws Exception {
        Path root = createExecutionRoot();
        Process process = startServer(root, ControlledMcpServerMain.class);
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                JsonNode tools = request(stdin, stdout, readerExecutor, 2, "tools/list", Map.of());
                assertExecutionToolContracts(tools);

                JsonNode invalid = request(stdin, stdout, readerExecutor, 3, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME,
                        "arguments", Map.of("module", "regression-nextjs-commerce", "environment", "dev", "headless", true, "timeoutSeconds", 1)));
                assertStructuredError(invalid, "INVALID_TIMEOUT");
                assertThat(Files.exists(root.resolve(".regression-mcp/runs"))).isFalse();

                Map<String, Object> startArguments = Map.of("module", "regression-nextjs-commerce", "environment", "dev", "headless", true, "timeoutSeconds", 30);
                JsonNode started = request(stdin, stdout, readerExecutor, 4, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME, "arguments", startArguments));
                assertThat(started.path("result").path("structuredContent").path("data").path("state").asText()).isEqualTo("QUEUED");
                String runId = started.path("result").path("structuredContent").path("data").path("runId").asText();
                JsonNode running = awaitState(stdin, stdout, readerExecutor, 5, runId, "RUNNING");
                assertThat(running.path("result").path("structuredContent").path("data").path("runId").asText()).isEqualTo(runId);
                JsonNode activeSummary = request(stdin, stdout, readerExecutor, 6, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME,
                        "arguments", Map.of("runId", runId)));
                assertStructuredError(activeSummary, "RUN_NOT_TERMINAL");
                JsonNode rejectedSummary = request(stdin, stdout, readerExecutor, 7, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME,
                        "arguments", Map.of("runId", runId, "unexpected", true)));
                assertRejected(rejectedSummary, "INVALID_ARGUMENTS");

                JsonNode extra = request(stdin, stdout, readerExecutor, 20, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME,
                        "arguments", Map.of("module", "regression-nextjs-commerce", "environment", "dev", "headless", true, "timeoutSeconds", 30, "unexpected", "x")));
                assertRejected(extra, "INVALID_ARGUMENTS");
                JsonNode second = request(stdin, stdout, readerExecutor, 21, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME, "arguments", startArguments));
                assertStructuredError(second, "RUN_ALREADY_ACTIVE");
                JsonNode unknown = request(stdin, stdout, readerExecutor, 22, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_RUN_TOOL_NAME,
                        "arguments", Map.of("runId", "run-00000000000000000000000000000000")));
                assertStructuredError(unknown, "RUN_NOT_FOUND");

                request(stdin, stdout, readerExecutor, 23, "tools/call", Map.of("name", RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME, "arguments", Map.of("runId", runId)));
                JsonNode cancelled = awaitState(stdin, stdout, readerExecutor, 24, runId, "CANCELLED");
                JsonNode repeated = request(stdin, stdout, readerExecutor, 25, "tools/call", Map.of("name", RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME, "arguments", Map.of("runId", runId)));
                assertThat(repeated.path("result").path("structuredContent").path("data").path("state").asText()).isEqualTo("CANCELLED");
                JsonNode unavailable = request(stdin, stdout, readerExecutor, 251, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME,
                        "arguments", Map.of("runId", runId)));
                assertStructuredError(unavailable, "NOT_FOUND");
                JsonNode overview = request(stdin, stdout, readerExecutor, 26, "tools/call", Map.of("name", RegressionMcpServer.OVERVIEW_TOOL_NAME, "arguments", Map.of()));
                assertThat(overview.path("result").path("isError").asBoolean()).isFalse();
            } finally { readerExecutor.shutdownNow(); }
            assertThat(process.waitFor(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally { terminateProcess(process); }
    }

    @Test
    void eofClosesAnActiveControlledRunAndPersistsCancellation() throws Exception {
        Path root = createExecutionRoot();
        Process process = startServer(root, ControlledMcpServerMain.class);
        String runId;
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        try {
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                JsonNode started = request(stdin, stdout, readerExecutor, 2, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME,
                        "arguments", Map.of("module", "regression-nextjs-commerce", "environment", "dev", "headless", true, "timeoutSeconds", 30)));
                runId = started.path("result").path("structuredContent").path("data").path("runId").asText();
                awaitState(stdin, stdout, readerExecutor, 3, runId, "RUNNING");
            }
            assertThat(process.waitFor(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            JsonNode status = JSON.readTree(Files.readString(root.resolve(".regression-mcp/runs").resolve(runId).resolve("status.json")));
            assertThat(status.path("snapshot").path("state").asText()).isEqualTo("CANCELLED");
            assertThat(status.path("ownedProcesses").size()).isGreaterThanOrEqualTo(1);
        } finally {
            readerExecutor.shutdownNow();
            terminateProcess(process);
        }
    }

    /** Gate 14.4 completion criterion, exercised end to end over real STDIO: for a genuine failing run, get its
     * summary, see the failure cause, list its artifacts, read a permitted one by artifactId, and confirm a
     * foreign artifactId and a foreign runId are both rejected as structured NOT_FOUND, never an exception. */
    @Test
    void servesFailureArtifactToolsForARealFailingRunAndRejectsForeignRequests() throws Exception {
        Path root = createExecutionRoot();
        Process process = startServer(root, FailingWithArtifactsMcpServerMain.class);
        try {
            ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                initialize(stdin, stdout, readerExecutor);
                Map<String, Object> startArguments = Map.of("module", "regression-nextjs-commerce", "environment", "dev", "headless", true, "timeoutSeconds", 30);
                JsonNode started = request(stdin, stdout, readerExecutor, 2, "tools/call", Map.of("name", RegressionMcpServer.START_TEST_RUN_TOOL_NAME, "arguments", startArguments));
                String runId = started.path("result").path("structuredContent").path("data").path("runId").asText();
                JsonNode failed = awaitState(stdin, stdout, readerExecutor, 3, runId, "FAILED");
                assertThat(failed.path("result").path("structuredContent").path("data").path("state").asText()).isEqualTo("FAILED");

                JsonNode summary = request(stdin, stdout, readerExecutor, 20, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME, "arguments", Map.of("runId", runId)));
                assertThat(summary.path("result").path("structuredContent").path("data").path("failures").asInt()).isEqualTo(1);

                JsonNode failureSummary = request(stdin, stdout, readerExecutor, 21, "tools/call", Map.of("name", RegressionMcpServer.GET_FAILURE_SUMMARY_TOOL_NAME, "arguments", Map.of("runId", runId)));
                assertThat(failureSummary.path("result").path("structuredContent").path("data").path("failureRecords")).hasSize(1);

                JsonNode artifacts = request(stdin, stdout, readerExecutor, 22, "tools/call", Map.of("name", RegressionMcpServer.GET_FAILURE_ARTIFACTS_TOOL_NAME, "arguments", Map.of("runId", runId)));
                JsonNode artifactList = artifacts.path("result").path("structuredContent").path("data").path("artifacts");
                assertThat(artifactList).hasSize(3);
                String screenshotArtifactId = null;
                for (JsonNode artifact : artifactList) {
                    assertThat(artifact.path("relativePath").asText()).doesNotContain(root.toString());
                    assertThat(artifact.path("name").asText()).doesNotContain(root.toString());
                    if ("image/png".equals(artifact.path("mimeType").asText())) screenshotArtifactId = artifact.path("artifactId").asText();
                }
                assertThat(screenshotArtifactId).isNotNull();

                JsonNode read = request(stdin, stdout, readerExecutor, 23, "tools/call", Map.of("name", RegressionMcpServer.READ_FAILURE_ARTIFACT_TOOL_NAME,
                        "arguments", Map.of("runId", runId, "artifactId", screenshotArtifactId)));
                assertThat(read.path("result").path("structuredContent").path("data").path("mimeType").asText()).isEqualTo("image/png");
                assertThat(read.path("result").path("structuredContent").path("data").path("content").asText()).isNotBlank();

                JsonNode foreignArtifact = request(stdin, stdout, readerExecutor, 24, "tools/call", Map.of("name", RegressionMcpServer.READ_FAILURE_ARTIFACT_TOOL_NAME,
                        "arguments", Map.of("runId", runId, "artifactId", "0".repeat(32))));
                assertStructuredError(foreignArtifact, "NOT_FOUND");

                JsonNode staleRun = request(stdin, stdout, readerExecutor, 25, "tools/call", Map.of("name", RegressionMcpServer.GET_FAILURE_ARTIFACTS_TOOL_NAME,
                        "arguments", Map.of("runId", "run-00000000000000000000000000000000")));
                assertStructuredError(staleRun, "RUN_NOT_FOUND");
            } finally { readerExecutor.shutdownNow(); }
        } finally { terminateProcess(process); }
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
        assertThat(toolsList.path("result").path("tools").size()).isEqualTo(11);
        for (JsonNode tool : toolsList.path("result").path("tools")) {
            assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
        }
        assertThat(toolsList.path("result").path("tools").get(4).path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        assertThat(toolsList.path("result").path("tools").get(4).path("annotations").path("destructiveHint").asBoolean()).isTrue();
        assertThat(toolsList.path("result").path("tools").get(5).path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(toolsList.path("result").path("tools").get(2).path("name").asText())
                .isEqualTo(RegressionMcpServer.LIST_FEATURES_TOOL_NAME);
        assertThat(toolsList.path("result").path("tools").get(3).path("name").asText())
                .isEqualTo(RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME);
    }

    private void assertExecutionToolContracts(JsonNode tools) {
        assertToolList(tools);
        Map<String, JsonNode> byName = new java.util.HashMap<>();
        for (JsonNode tool : tools.path("result").path("tools")) byName.put(tool.path("name").asText(), tool);
        assertThat(byName.keySet()).containsExactlyInAnyOrder(RegressionMcpServer.OVERVIEW_TOOL_NAME, RegressionMcpServer.LIST_MODULES_TOOL_NAME,
                RegressionMcpServer.LIST_FEATURES_TOOL_NAME, RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME, RegressionMcpServer.START_TEST_RUN_TOOL_NAME,
                RegressionMcpServer.GET_TEST_RUN_TOOL_NAME, RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME, RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME,
                RegressionMcpServer.GET_FAILURE_SUMMARY_TOOL_NAME, RegressionMcpServer.GET_FAILURE_ARTIFACTS_TOOL_NAME,
                RegressionMcpServer.READ_FAILURE_ARTIFACT_TOOL_NAME);
        assertThat(byName.get(RegressionMcpServer.START_TEST_RUN_TOOL_NAME).path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        assertThat(byName.get(RegressionMcpServer.START_TEST_RUN_TOOL_NAME).path("annotations").path("destructiveHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.START_TEST_RUN_TOOL_NAME).path("annotations").path("idempotentHint").asBoolean()).isFalse();
        assertThat(byName.get(RegressionMcpServer.START_TEST_RUN_TOOL_NAME).path("annotations").path("openWorldHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.GET_TEST_RUN_TOOL_NAME).path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.GET_TEST_RUN_TOOL_NAME).path("annotations").path("destructiveHint").asBoolean()).isFalse();
        assertThat(byName.get(RegressionMcpServer.GET_TEST_RUN_TOOL_NAME).path("annotations").path("idempotentHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.GET_TEST_RUN_TOOL_NAME).path("annotations").path("openWorldHint").asBoolean()).isFalse();
        assertThat(byName.get(RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME).path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        assertThat(byName.get(RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME).path("annotations").path("destructiveHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME).path("annotations").path("idempotentHint").asBoolean()).isTrue();
        assertThat(byName.get(RegressionMcpServer.CANCEL_TEST_RUN_TOOL_NAME).path("annotations").path("openWorldHint").asBoolean()).isFalse();
        JsonNode summary = byName.get(RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME);
        assertThat(summary.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(summary.path("annotations").path("destructiveHint").asBoolean()).isFalse();
        assertThat(summary.path("annotations").path("idempotentHint").asBoolean()).isTrue();
        assertThat(summary.path("annotations").path("openWorldHint").asBoolean()).isFalse();
        assertThat(summary.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
    }

    private JsonNode awaitState(BufferedWriter stdin, BufferedReader stdout, ExecutorService readerExecutor, int requestId, String runId, String state) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JsonNode response;
        do {
            response = request(stdin, stdout, readerExecutor, requestId++, "tools/call", Map.of("name", RegressionMcpServer.GET_TEST_RUN_TOOL_NAME, "arguments", Map.of("runId", runId)));
            if (response.path("result").path("isError").asBoolean()) throw new AssertionError("Polling failed: " + response);
            String observed = response.path("result").path("structuredContent").path("data").path("state").asText();
            if (state.equals(observed)) return response;
            if (List.of("PASSED", "FAILED", "CANCELLED", "TIMED_OUT", "ERROR").contains(observed)) {
                throw new AssertionError("Run reached " + observed + " while awaiting " + state + ": " + response);
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Run did not reach " + state);
    }

    private JsonNode request(BufferedWriter stdin, BufferedReader stdout, ExecutorService readerExecutor, int id,
            String method, Map<String, Object> params) throws Exception {
        stdin.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params)));
        stdin.newLine();
        stdin.flush();
        Future<String> response = readerExecutor.submit(stdout::readLine);
        String line = response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(line).as("stdout must contain only an MCP JSON-RPC response").isNotBlank();
        JsonNode message = JSON.readTree(line);
        assertThat(message.path("jsonrpc").asText()).isEqualTo("2.0");
        return message;
    }

    private void assertStructuredError(JsonNode response, String code) {
        assertThat(response.path("result").path("isError").asBoolean()).isTrue();
        assertThat(response.path("result").path("structuredContent").path("status").asText()).isEqualTo("error");
        assertThat(response.path("result").path("structuredContent").path("error").path("code").asText()).isEqualTo(code);
        assertThat(response.path("result").path("structuredContent").path("data").isMissingNode()).isTrue();
    }

    private void assertNoApplicationChildProcesses(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            assertThat(descendants).as("only the Windows Java launcher/runtime handoff may be a server-process descendant")
                    .hasSizeLessThanOrEqualTo(1);
        }
        else {
            assertThat(descendants).as("the server must not create child processes").isEmpty();
        }
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
        return startServer(root, RegressionMcpServer.class);
    }

    private void assertRejected(JsonNode response, String code) {
        if (response.path("result").path("structuredContent").path("status").asText().equals("error")) {
            assertStructuredError(response, code);
        } else {
            assertThat(response.path("error").isObject() || response.path("result").path("isError").asBoolean()).isTrue();
        }
    }

    private Process startServer(Path root, Class<?> mainClass) throws IOException {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", testClasspath, mainClass.getName());
        builder.environment().put(RepositoryRootResolver.ENVIRONMENT_VARIABLE, root.toString());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
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

    private Path createExecutionRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules><module>regression-nextjs-commerce</module></modules></project>");
        Path module = temporaryDirectory.resolve("regression-nextjs-commerce"); Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        return temporaryDirectory;
    }

    private Path createRootWithMalformedFeature() throws Exception {
        Path root = createRoot();
        Path feature = root.resolve("commerce/src/test/resources/features/broken.feature");
        Files.createDirectories(feature.getParent());
        Files.writeString(feature, "Feature: broken\n  Scenario: bad\n    Given x\n    \"\"\"\n    unclosed");
        return root;
    }

    private Path createRootWithMalformedPom() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>");
        return temporaryDirectory;
    }

    private Path createRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules><module>commerce</module></modules></project>");
        Path module = temporaryDirectory.resolve("commerce");
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        return temporaryDirectory;
    }

    private Map<String, String> fixtureSnapshot(Path root) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                snapshot.put(root.relativize(path).toString().replace('\\', '/'),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return snapshot;
    }
}
