package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.aqa.mcp.execution.ControlledCoordinatorFactory;
import com.aqa.mcp.execution.FailureArtifact;
import com.aqa.mcp.execution.RunSnapshot;
import com.aqa.mcp.execution.StartTestRunRequest;
import com.aqa.mcp.execution.TestRunCoordinator;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionMcpServerContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesAnExplicitNoArgumentInputContractAndStructuredOutputContract() {
        SyncToolSpecification toolSpecification = RegressionMcpServer.overviewTool(validRoot());

        assertThat(toolSpecification.tool().name()).isEqualTo(RegressionMcpServer.OVERVIEW_TOOL_NAME);
        assertThat(toolSpecification.tool().inputSchema()).isEqualTo(Map.of(
                "type", "object",
                "additionalProperties", false));
        assertThat(toolSpecification.tool().outputSchema()).containsKey("oneOf");
        assertThat(toolSpecification.tool().annotations().readOnlyHint()).isTrue();
        assertThat(toolSpecification.tool().annotations().destructiveHint()).isFalse();
        assertThat(toolSpecification.tool().annotations().idempotentHint()).isTrue();
        assertThat(toolSpecification.tool().annotations().openWorldHint()).isFalse();
    }

    @Test
    void producesStableStructuredOutput() throws Exception {
        FrameworkOverview overview = FrameworkOverview.forRoot(validRoot());

        assertThat(overview.asToolOutput())
                .isEqualTo(overview.asToolOutput())
                .isEqualTo(Map.of(
                        "status", "ok",
                        "data", Map.of(
                                "name", "regression",
                                "root", temporaryDirectory.toRealPath().toString().replace('\\', '/'),
                                "javaVersion", "21",
                                "buildTool", "Maven",
                                "availability", "AVAILABLE")));
    }

    @Test
    void exposesTheReadOnlyModuleListContract() {
        SyncToolSpecification toolSpecification = RegressionMcpServer.listModulesTool(validRoot());

        assertThat(toolSpecification.tool().name()).isEqualTo(RegressionMcpServer.LIST_MODULES_TOOL_NAME);
        assertThat(toolSpecification.tool().inputSchema()).isEqualTo(Map.of(
                "type", "object",
                "additionalProperties", false));
        assertThat(toolSpecification.tool().outputSchema()).containsKey("oneOf");
        assertThat(toolSpecification.tool().annotations().readOnlyHint()).isTrue();
        assertThat(toolSpecification.tool().annotations().destructiveHint()).isFalse();
        assertThat(toolSpecification.tool().annotations().idempotentHint()).isTrue();
        assertThat(toolSpecification.tool().annotations().openWorldHint()).isFalse();
    }

    @Test
    void exposesClosedReadOnlyFeatureAndScenarioContracts() {
        for (SyncToolSpecification tool : List.of(RegressionMcpServer.featureListTool(validRoot()),
                RegressionMcpServer.scenarioListTool(validRoot()))) {
            assertThat(tool.tool().inputSchema().get("additionalProperties")).isEqualTo(false);
            assertThat(tool.tool().outputSchema()).containsKey("oneOf");
            assertThat(tool.tool().annotations().readOnlyHint()).isTrue();
            assertThat(tool.tool().annotations().destructiveHint()).isFalse();
            assertThat(tool.tool().annotations().idempotentHint()).isTrue();
            assertThat(tool.tool().annotations().openWorldHint()).isFalse();
        }
        assertThat(RegressionMcpServer.featureListTool(validRoot()).tool().name())
                .isEqualTo(RegressionMcpServer.LIST_FEATURES_TOOL_NAME);
        assertThat(RegressionMcpServer.scenarioListTool(validRoot()).tool().name())
                .isEqualTo(RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME);
    }

    /** Both list handlers re-resolve REGRESSION_ROOT on every call, so a root that was valid when the tool
     * specification was built and is broken at call time drives a plain IllegalArgumentException out of
     * RepositoryRootResolver.resolve — the only exception that reaches each handler's final catch clause.
     * That broken-repository condition must surface identically from both tools as REPOSITORY_ERROR. */
    @Test
    void bothListToolsReportABrokenRepositoryRootAsRepositoryError() throws Exception {
        SyncToolSpecification featureList = RegressionMcpServer.featureListTool(validRoot());
        SyncToolSpecification scenarioList = RegressionMcpServer.scenarioListTool(validRoot());
        Files.delete(temporaryDirectory.resolve("pom.xml"));

        CallToolResult featureResult = featureList.callHandler().apply(null, new CallToolRequest(
                RegressionMcpServer.LIST_FEATURES_TOOL_NAME, Map.of("module", "regression-nextjs-commerce")));
        CallToolResult scenarioResult = scenarioList.callHandler().apply(null, new CallToolRequest(
                RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME, Map.of("module", "regression-nextjs-commerce")));

        assertThat(List.of(errorCode(featureResult), errorCode(scenarioResult)))
                .as("[list_features code, list_scenarios code] for a broken REGRESSION_ROOT")
                .containsExactly("REPOSITORY_ERROR", "REPOSITORY_ERROR");
    }

    private static String errorCode(CallToolResult result) {
        assertThat(result.isError()).isTrue();
        @SuppressWarnings("unchecked") Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        @SuppressWarnings("unchecked") Map<String, Object> error = (Map<String, Object>) structured.get("error");
        return (String) error.get("code");
    }

    /** The converse of {@link #bothListToolsReportABrokenRepositoryRootAsRepositoryError()}: a genuinely bad
     * argument set (here an unknown key alongside a valid {@code module}) must still surface as
     * INVALID_ARGUMENTS from scenarioListTool, not REPOSITORY_ERROR. moduleArgument raises this as a
     * RepositoryInspectionException caught by the handler's FIRST catch clause; this pins that the two catch
     * clauses stay distinct, so a later fold of the second into the first cannot silently reclassify a bad
     * argument as a repository failure. */
    @Test
    void listScenariosStillReportsABadArgumentAsInvalidArguments() {
        SyncToolSpecification scenarioList = RegressionMcpServer.scenarioListTool(validRoot());

        CallToolResult result = scenarioList.callHandler().apply(null, new CallToolRequest(
                RegressionMcpServer.LIST_SCENARIOS_TOOL_NAME, Map.of("module", "regression-nextjs-commerce", "bogus", "x")));

        assertThat(errorCode(result)).isEqualTo("INVALID_ARGUMENTS");
    }

    @Test
    void givesGetAndCancelTestRunToolsDistinctNonBlankDescriptions() {
        TestRunCoordinator coordinator = new TestRunCoordinator(validRoot().path(), () -> { throw new AssertionError("Description contract must not validate a run."); });
        try {
            String getDescription = RegressionMcpServer.getTestRunTool(coordinator).tool().description();
            String cancelDescription = RegressionMcpServer.cancelTestRunTool(coordinator).tool().description();

            assertThat(getDescription).isNotBlank();
            assertThat(cancelDescription).isNotBlank();
            assertThat(getDescription).isNotEqualTo(cancelDescription);
            assertThat(getDescription).as("get tool's description should convey state retrieval").containsIgnoringCase("state");
            assertThat(cancelDescription).as("cancel tool's description should convey cancellation").containsIgnoringCase("cancel");
        } finally { coordinator.close(); }
    }

    /** A run cancelled before it ever produces a Surefire report must never publish a skippedTests count: the
     * runOutput map must OMIT the key entirely (never a JSON null) when RunSnapshot.skippedTests() is null. */
    @Test
    void getTestRunOmitsSkippedTestsKeyWhenNoSurefireReportWasCaptured() throws Exception {
        Path root = executionRoot();
        TestRunCoordinator coordinator = ControlledCoordinatorFactory.waitingCoordinator(root);
        try {
            RunSnapshot started = coordinator.start(new StartTestRunRequest("regression-nextjs-commerce", null, "dev", true, 30), Map.of());
            coordinator.cancel(started.runId());
            RunSnapshot terminal = awaitTerminal(coordinator, started.runId());
            assertThat(terminal.state().name()).isEqualTo("CANCELLED");
            assertThat(terminal.skippedTests()).isNull();

            SyncToolSpecification tool = RegressionMcpServer.getTestRunTool(coordinator);
            CallToolResult result = tool.callHandler().apply(null, new CallToolRequest(RegressionMcpServer.GET_TEST_RUN_TOOL_NAME,
                    Map.of("runId", started.runId())));

            assertThat(result.isError()).isFalse();
            @SuppressWarnings("unchecked") Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) structured.get("data");
            assertThat(data).doesNotContainKey("skippedTests");
        } finally { coordinator.close(); }
    }

    @Test
    void exposesTheClosedReadOnlySurefireSummaryContract() {
        TestRunCoordinator coordinator = new TestRunCoordinator(validRoot().path(), () -> { throw new AssertionError("Summary contract must not validate a run."); });
        try {
            SyncToolSpecification tool = RegressionMcpServer.testSummaryTool(coordinator);
            assertThat(tool.tool().name()).isEqualTo(RegressionMcpServer.GET_TEST_SUMMARY_TOOL_NAME);
            assertThat(tool.tool().inputSchema()).isEqualTo(Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))));
            assertThat(tool.tool().annotations().readOnlyHint()).isTrue(); assertThat(tool.tool().annotations().destructiveHint()).isFalse();
            assertThat(tool.tool().annotations().idempotentHint()).isTrue(); assertThat(tool.tool().annotations().openWorldHint()).isFalse();
        } finally { coordinator.close(); }
    }
    @Test
    void exposesTheClosedReadOnlyFailureSummaryContract() {
        TestRunCoordinator coordinator = new TestRunCoordinator(validRoot().path(), () -> { throw new AssertionError("Failure summary contract must not validate a run."); });
        try {
            SyncToolSpecification tool = RegressionMcpServer.failureSummaryTool(coordinator);
            assertThat(tool.tool().name()).isEqualTo(RegressionMcpServer.GET_FAILURE_SUMMARY_TOOL_NAME);
            assertThat(tool.tool().inputSchema()).isEqualTo(Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))));
            assertThat(tool.tool().annotations().readOnlyHint()).isTrue(); assertThat(tool.tool().annotations().destructiveHint()).isFalse();
            assertThat(tool.tool().annotations().idempotentHint()).isTrue(); assertThat(tool.tool().annotations().openWorldHint()).isFalse();
        } finally { coordinator.close(); }
    }
    @Test
    void exposesTheClosedReadOnlyFailureArtifactsContract() {
        TestRunCoordinator coordinator = new TestRunCoordinator(validRoot().path(), () -> { throw new AssertionError("Failure artifacts contract must not validate a run."); });
        try {
            SyncToolSpecification tool = RegressionMcpServer.failureArtifactsTool(coordinator);
            assertThat(tool.tool().name()).isEqualTo(RegressionMcpServer.GET_FAILURE_ARTIFACTS_TOOL_NAME);
            assertThat(tool.tool().inputSchema()).isEqualTo(Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))));
            assertThat(tool.tool().outputSchema()).containsKey("oneOf");
            assertThat(tool.tool().annotations().readOnlyHint()).isTrue(); assertThat(tool.tool().annotations().destructiveHint()).isFalse();
            assertThat(tool.tool().annotations().idempotentHint()).isTrue(); assertThat(tool.tool().annotations().openWorldHint()).isFalse();
        } finally { coordinator.close(); }
    }
    @Test
    void exposesTheClosedReadOnlyReadFailureArtifactContract() {
        TestRunCoordinator coordinator = new TestRunCoordinator(validRoot().path(), () -> { throw new AssertionError("Read failure artifact contract must not validate a run."); });
        try {
            SyncToolSpecification tool = RegressionMcpServer.readFailureArtifactTool(coordinator);
            assertThat(tool.tool().name()).isEqualTo(RegressionMcpServer.READ_FAILURE_ARTIFACT_TOOL_NAME);
            assertThat(tool.tool().inputSchema()).isEqualTo(Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("runId", "artifactId"), "properties", Map.of("runId", Map.of("type", "string"), "artifactId", Map.of("type", "string"))));
            assertThat(tool.tool().outputSchema()).containsKey("oneOf");
            assertThat(tool.tool().annotations().readOnlyHint()).isTrue(); assertThat(tool.tool().annotations().destructiveHint()).isFalse();
            assertThat(tool.tool().annotations().idempotentHint()).isTrue(); assertThat(tool.tool().annotations().openWorldHint()).isFalse();
        } finally { coordinator.close(); }
    }
    /** Exercises the tool-layer ARTIFACT_TOO_LARGE bound against a genuine published artifact. RunStore and
     * ReportCapture are package-private to com.aqa.mcp.execution, so this deliberately drives a real controlled
     * run through TestRunCoordinator's public API rather than poking store internals from this package. */
    @Test
    void rejectsAnArtifactExceedingItsBoundedResponseContract() throws Exception {
        Path root = executionRoot();
        TestRunCoordinator coordinator = ControlledCoordinatorFactory.failingCoordinatorWithArtifacts(root);
        try {
            RunSnapshot started = coordinator.start(new StartTestRunRequest("regression-nextjs-commerce", null, "dev", true, 30), Map.of());
            RunSnapshot terminal = awaitTerminal(coordinator, started.runId());
            assertThat(terminal.state().name()).isEqualTo("FAILED");

            List<FailureArtifact> artifacts = coordinator.artifacts(started.runId());
            FailureArtifact oversized = artifacts.stream().filter(artifact -> artifact.relativePath().equals("oversized-log.txt")).findFirst().orElseThrow();

            SyncToolSpecification tool = RegressionMcpServer.readFailureArtifactTool(coordinator);
            CallToolResult result = tool.callHandler().apply(null, new CallToolRequest(RegressionMcpServer.READ_FAILURE_ARTIFACT_TOOL_NAME,
                    Map.of("runId", started.runId(), "artifactId", oversized.artifactId())));

            assertThat(result.isError()).isTrue();
            @SuppressWarnings("unchecked") Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
            @SuppressWarnings("unchecked") Map<String, Object> error = (Map<String, Object>) structured.get("error");
            assertThat(error.get("code")).isEqualTo("ARTIFACT_TOO_LARGE");
        } finally { coordinator.close(); }
    }

    private RunSnapshot awaitTerminal(TestRunCoordinator coordinator, String runId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        RunSnapshot snapshot;
        do {
            snapshot = coordinator.get(runId);
            if (snapshot.terminal()) return snapshot;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Run did not reach a terminal state in time: " + snapshot);
    }

    private Path executionRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules><module>regression-nextjs-commerce</module></modules></project>");
        Path module = temporaryDirectory.resolve("regression-nextjs-commerce");
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        return temporaryDirectory;
    }

    private RepositoryRoot validRoot() {
        try {
            Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project/>");
            return RepositoryRootResolver.resolve(temporaryDirectory);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
