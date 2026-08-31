package com.aqa.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aqa.mcp.execution.ArtifactContent;
import com.aqa.mcp.execution.CloseAwareInputStream;
import com.aqa.mcp.execution.ExecutionPlanningException;
import com.aqa.mcp.execution.FailureArtifact;
import com.aqa.mcp.execution.RunSnapshot;
import com.aqa.mcp.execution.StartTestRunRequest;
import com.aqa.mcp.execution.SurefireSummary;
import com.aqa.mcp.execution.TestRunCoordinator;
import com.aqa.mcp.validation.ArchitectureTool;
import com.aqa.mcp.validation.FrameworkConventionsTool;
import com.aqa.mcp.validation.ModuleBoundariesTool;
import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

public final class RegressionMcpServer {

    private static final int MAX_FAILURE_SUMMARY_RESPONSE_BYTES = 96 * 1024;
    private static final int MAX_ARTIFACT_READ_RESPONSE_BYTES = 2 * 1024 * 1024;

    static final String SERVER_NAME = "regression-mcp-server";
    static final String SERVER_VERSION = "1.0.0";
    static final String OVERVIEW_TOOL_NAME = "regression_get_framework_overview";
    static final String LIST_MODULES_TOOL_NAME = "regression_list_modules";
    static final String LIST_FEATURES_TOOL_NAME = "regression_list_features";
    static final String LIST_SCENARIOS_TOOL_NAME = "regression_list_scenarios";
    static final String START_TEST_RUN_TOOL_NAME = "regression_start_test_run";
    static final String GET_TEST_RUN_TOOL_NAME = "regression_get_test_run";
    static final String CANCEL_TEST_RUN_TOOL_NAME = "regression_cancel_test_run";
    static final String GET_TEST_SUMMARY_TOOL_NAME = "regression_get_test_summary";
    static final String GET_FAILURE_SUMMARY_TOOL_NAME = "regression_get_failure_summary";
    static final String GET_FAILURE_ARTIFACTS_TOOL_NAME = "regression_get_failure_artifacts";
    static final String READ_FAILURE_ARTIFACT_TOOL_NAME = "regression_read_failure_artifact";
    static final String VALIDATE_MODULE_BOUNDARIES_TOOL_NAME = ModuleBoundariesTool.TOOL_NAME;
    static final String VALIDATE_FRAMEWORK_CONVENTIONS_TOOL_NAME = FrameworkConventionsTool.TOOL_NAME;
    static final String VALIDATE_ARCHITECTURE_TOOL_NAME = ArchitectureTool.TOOL_NAME;
    private static final String INSTRUCTIONS =
            "This is a local framework inspection server for the repository configured by REGRESSION_ROOT. "
                    + "Most tools are deterministic and read-only. Three explicitly authorized execution tools "
                    + "(regression_start_test_run, regression_get_test_run, regression_cancel_test_run) start, "
                    + "observe, and cancel test runs for allow-listed modules; they are the only tools with side "
                    + "effects.";

    private RegressionMcpServer() {
    }

    public static void main(String[] arguments) {
        try {
            createServer(RepositoryRootResolver.resolve(System.getenv()));
        }
        catch (IllegalArgumentException exception) {
            System.err.println("Regression MCP server startup failed: " + exception.getMessage());
            System.exit(2);
        }
    }

    static void createServer(RepositoryRoot repositoryRoot) {
        TestRunCoordinator coordinator = new TestRunCoordinator(repositoryRoot.path(), () -> ExecutionPlanningFactory.validatorFor(repositoryRoot));
        createServer(repositoryRoot, coordinator);
    }

    /** Package-private composition seam used only by the test-classpath STDIO bootstrap. */
    static void createServer(RepositoryRoot repositoryRoot, TestRunCoordinator coordinator) {
        Runtime.getRuntime().addShutdownHook(new Thread(coordinator::close, "regression-mcp-shutdown"));
        InputStream input = new CloseAwareInputStream(System.in, coordinator::close);
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper(), input, System.out))
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(INSTRUCTIONS)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(overviewTool(repositoryRoot), listModulesTool(repositoryRoot), featureListTool(repositoryRoot), scenarioListTool(repositoryRoot),
                        startTestRunTool(coordinator), getTestRunTool(coordinator), cancelTestRunTool(coordinator), testSummaryTool(coordinator),
                        failureSummaryTool(coordinator), failureArtifactsTool(coordinator), readFailureArtifactTool(coordinator),
                        ModuleBoundariesTool.tool(repositoryRoot.path(), () -> moduleTypeByName(repositoryRoot)),
                        FrameworkConventionsTool.tool(repositoryRoot.path(), () -> moduleTypeByName(repositoryRoot)),
                        ArchitectureTool.tool(repositoryRoot.path(), () -> moduleTypeByName(repositoryRoot)))
                .build();
    }

    /** Resolved fresh on every call (not cached at startup) so a malformed root pom.xml only fails the tool
     * call that needs it, matching every other read-only tool's per-request resolution instead of failing
     * server startup itself. */
    private static Map<String, String> moduleTypeByName(RepositoryRoot repositoryRoot) {
        Map<String, String> types = new java.util.LinkedHashMap<>();
        for (ModuleDescriptor module : ModuleList.forRoot(RepositoryRootResolver.resolve(repositoryRoot.path())).modules()) {
            types.put(module.name(), module.type().name());
        }
        return java.util.Collections.unmodifiableMap(types);
    }

    static SyncToolSpecification startTestRunTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(START_TEST_RUN_TOOL_NAME, ToolSchemas.startInputSchema())
                .description("Starts an allowed test run (regression-nextjs-commerce or regression-jhipster).").annotations(executionAnnotations(false, true, false, true)).outputSchema(ToolSchemas.runOutputSchema()).build())
                .callHandler((exchange, request) -> { try { return successResult(Map.of("status", "ok", "data", runOutput(coordinator.start(startRequest(request.arguments()), System.getenv())))); }
                    catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); } }).build();
    }

    static SyncToolSpecification getTestRunTool(TestRunCoordinator coordinator) {
        return runActionTool(GET_TEST_RUN_TOOL_NAME, coordinator, true, "Returns the current state of a server-generated test run.");
    }
    static SyncToolSpecification testSummaryTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(GET_TEST_SUMMARY_TOOL_NAME, ToolSchemas.runIdInputSchema())
                .description("Returns the published authoritative Surefire summary for a terminal server-generated run.")
                .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.summaryOutputSchema()).build()).callHandler((exchange, request) -> {
                    try { String id = runId(request.arguments()); return successResult(Map.of("status", "ok", "data", summaryOutput(id, coordinator.summary(id)))); }
                    catch (ExecutionPlanningException exception) { return errorResult(exception.code(), exception.getMessage()); }
                }).build();
    }
    static SyncToolSpecification failureSummaryTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(GET_FAILURE_SUMMARY_TOOL_NAME, ToolSchemas.runIdInputSchema())
                .description("Returns bounded authoritative Surefire failures and persisted optional Allure enrichment for a terminal server-generated run.")
                .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.failureSummaryOutputSchema()).build()).callHandler((exchange, request) -> {
                    try { String id = runId(request.arguments()); return failureSummaryResult(failureSummaryOutput(id, coordinator.failureSummary(id))); }
                    catch (ExecutionPlanningException exception) { return errorResult(exception.code(), exception.getMessage()); }
                }).build();
    }
    static SyncToolSpecification failureArtifactsTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(GET_FAILURE_ARTIFACTS_TOOL_NAME, ToolSchemas.runIdInputSchema())
                .description("Lists the server-published artifacts captured for a terminal server-generated run.")
                .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.artifactsOutputSchema()).build()).callHandler((exchange, request) -> {
                    try { String id = runId(request.arguments()); return successResult(artifactsOutput(id, coordinator.artifacts(id))); }
                    catch (ExecutionPlanningException exception) { return errorResult(exception.code(), exception.getMessage()); }
                }).build();
    }
    static SyncToolSpecification readFailureArtifactTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(READ_FAILURE_ARTIFACT_TOOL_NAME, ToolSchemas.artifactInputSchema())
                .description("Returns bounded, MIME-allow-listed bytes for one server-generated artifactId belonging to a terminal server-generated run.")
                .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.readArtifactOutputSchema()).build()).callHandler((exchange, request) -> {
                    try {
                        Map<String, String> arguments = artifactArguments(request.arguments());
                        ArtifactContent content = coordinator.readArtifact(arguments.get("runId"), arguments.get("artifactId"));
                        return readArtifactResult(readArtifactOutput(content));
                    } catch (ExecutionPlanningException exception) { return errorResult(exception.code(), exception.getMessage()); }
                }).build();
    }
    static SyncToolSpecification cancelTestRunTool(TestRunCoordinator coordinator) {
        return runActionTool(CANCEL_TEST_RUN_TOOL_NAME, coordinator, false, "Cancels an active server-generated test run.");
    }
    private static SyncToolSpecification runActionTool(String name, TestRunCoordinator coordinator, boolean get, String description) {
        return SyncToolSpecification.builder().tool(Tool.builder(name, ToolSchemas.runIdInputSchema()).description(description)
                .annotations(get ? executionAnnotations(true, false, true, false) : executionAnnotations(false, true, true, false)).outputSchema(ToolSchemas.runOutputSchema()).build()).callHandler((exchange, request) -> { try {
                    String id = runId(request.arguments()); RunSnapshot snapshot = get ? coordinator.get(id) : coordinator.cancel(id);
                    return successResult(Map.of("status", "ok", "data", runOutput(snapshot))); } catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); } }).build();
    }

    static SyncToolSpecification overviewTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(OVERVIEW_TOOL_NAME, ToolSchemas.inputSchema())
                        .description("Returns a deterministic overview of the local regression framework.")
                        .annotations(readOnlyAnnotations())
                        .outputSchema(ToolSchemas.outputSchema())
                        .build())
                .callHandler((exchange, request) -> {
                    if (request.arguments() != null && !request.arguments().isEmpty()) {
                        return errorResult("INVALID_ARGUMENTS", "This tool does not accept arguments.");
                    }
                    try {
                        Map<String, Object> output = FrameworkOverview.forRoot(
                                RepositoryRootResolver.resolve(repositoryRoot.path())).asToolOutput();
                        return successResult(output);
                    }
                    catch (IllegalArgumentException exception) {
                        return errorResult("REPOSITORY_ERROR", exception.getMessage());
                    }
                })
                .build();
    }

    static SyncToolSpecification listModulesTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(LIST_MODULES_TOOL_NAME, ToolSchemas.inputSchema())
                        .description("Lists the reactor modules declared by the root parent pom.xml.")
                        .annotations(readOnlyAnnotations())
                        .outputSchema(ToolSchemas.moduleListOutputSchema())
                        .build())
                .callHandler((exchange, request) -> {
                    if (request.arguments() != null && !request.arguments().isEmpty()) {
                        return moduleErrorResult("INVALID_ARGUMENTS", "This tool does not accept arguments.");
                    }
                    try {
                        return successResult(ModuleList.forRoot(
                                RepositoryRootResolver.resolve(repositoryRoot.path())).asToolOutput());
                    }
                    catch (RepositoryInspectionException exception) {
                        return moduleErrorResult(exception.code(), exception.getMessage());
                    }
                    catch (IllegalArgumentException exception) {
                        return moduleErrorResult("REPOSITORY_ERROR", exception.getMessage());
                    }
                })
                .build();
    }

    static SyncToolSpecification featureListTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(LIST_FEATURES_TOOL_NAME, ToolSchemas.moduleInputSchema(false))
                        .description("Lists parsed Gherkin features below a declared module's feature root.")
                        .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.featureOutputSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        String module = moduleArgument(request.arguments(), false);
                        FeatureDiscovery.DiscoveryResult discovery = FeatureDiscovery.discover(
                                RepositoryRootResolver.resolve(repositoryRoot.path()), module);
                        return successResult(featureOutput(discovery));
                    } catch (RepositoryInspectionException exception) {
                        return moduleErrorResult(exception.code(), exception.getMessage());
                    } catch (IllegalArgumentException exception) {
                        return moduleErrorResult("REPOSITORY_ERROR", exception.getMessage());
                    }
                }).build();
    }

    static SyncToolSpecification scenarioListTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(LIST_SCENARIOS_TOOL_NAME, ToolSchemas.moduleInputSchema(true))
                        .description("Lists executable Cucumber scenarios below a declared module's feature root.")
                        .annotations(readOnlyAnnotations()).outputSchema(ToolSchemas.scenarioOutputSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        String module = moduleArgument(request.arguments(), true);
                        Expression expression = tagExpression(request.arguments());
                        FeatureDiscovery.DiscoveryResult discovery = FeatureDiscovery.discover(
                                RepositoryRootResolver.resolve(repositoryRoot.path()), module);
                        List<Map<String, Object>> scenarios = discovery.scenarios().stream()
                                .filter(scenario -> expression == null || expression.evaluate(scenario.tags()))
                                .map(RegressionMcpServer::scenarioOutput).toList();
                        return successResult(Map.of("status", "ok", "data", Map.of("module", module, "scenarios", scenarios)));
                    } catch (RepositoryInspectionException exception) {
                        return moduleErrorResult(exception.code(), exception.getMessage());
                    } catch (IllegalArgumentException exception) {
                        return moduleErrorResult("REPOSITORY_ERROR", exception.getMessage());
                    }
                }).build();
    }

    private static String moduleArgument(Map<String, Object> arguments, boolean tagsAllowed) {
        if (arguments == null || !arguments.containsKey("module") || !(arguments.get("module") instanceof String module)
                || module.isBlank() || arguments.size() > (tagsAllowed ? 2 : 1)
                || arguments.keySet().stream().anyMatch(key -> !key.equals("module") && (!tagsAllowed || !key.equals("tags")))) {
            throw new RepositoryInspectionException("INVALID_ARGUMENTS", "module must be a non-blank string and arguments must be known.");
        }
        if (tagsAllowed && arguments.containsKey("tags") && !(arguments.get("tags") instanceof String)) {
            throw new RepositoryInspectionException("INVALID_ARGUMENTS", "tags must be a string.");
        }
        return module;
    }

    private static Expression tagExpression(Map<String, Object> arguments) {
        if (arguments == null || !arguments.containsKey("tags")) return null;
        String tags = (String) arguments.get("tags");
        if (tags.isBlank()) throw new RepositoryInspectionException("INVALID_TAG_EXPRESSION", "tags must not be blank.");
        try {
            return TagExpressionParser.parse(tags);
        } catch (RuntimeException exception) {
            throw new RepositoryInspectionException("INVALID_TAG_EXPRESSION", "Invalid tag expression: " + exception.getMessage());
        }
    }

    private static Map<String, Object> featureOutput(FeatureDiscovery.DiscoveryResult discovery) {
        List<Map<String, Object>> features = discovery.features().stream().map(feature -> Map.<String, Object>of(
                "name", feature.name(), "language", feature.language(), "tags", feature.tags(), "path", feature.path(),
                "line", feature.line(), "scenarioCount", feature.scenarios().size())).toList();
        return Map.of("status", "ok", "data", Map.of("module", discovery.module(), "featureRoot", FeatureDiscovery.FEATURE_ROOT,
                "featureRootExists", discovery.featureRootExists(), "features", features));
    }

    private static Map<String, Object> scenarioOutput(FeatureDiscovery.ExecutableScenario scenario) {
        return Map.of("feature", scenario.feature(), "name", scenario.name(), "type", scenario.type(),
                "tags", scenario.tags(), "path", scenario.path(), "line", scenario.line());
    }

    private static StartTestRunRequest startRequest(Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(java.util.Set.of("module", "environment", "headless", "timeoutSeconds"))
                && !arguments.keySet().equals(java.util.Set.of("module", "environment", "headless", "timeoutSeconds", "tags"))) {
            throw new ExecutionPlanningException("INVALID_ARGUMENTS", "Only module, tags, environment, headless, and timeoutSeconds are accepted.");
        }
        Object module = arguments.get("module"), environment = arguments.get("environment"), headless = arguments.get("headless"), timeout = arguments.get("timeoutSeconds"), tags = arguments.get("tags");
        if (!(module instanceof String) || !(environment instanceof String) || !(headless instanceof Boolean) || !(timeout instanceof Integer)
                || tags != null && !(tags instanceof String)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "Execution request fields have invalid types.");
        return new StartTestRunRequest((String) module, (String) tags, (String) environment, (Boolean) headless, (Integer) timeout);
    }

    private static String runId(Map<String, Object> arguments) {
        if (arguments == null || arguments.size() != 1 || !(arguments.get("runId") instanceof String id))
            throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId must be the only argument.");
        return id;
    }

    private static Map<String, String> artifactArguments(Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(java.util.Set.of("runId", "artifactId"))
                || !(arguments.get("runId") instanceof String runId) || !(arguments.get("artifactId") instanceof String artifactId)) {
            throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId and artifactId must be the only string arguments.");
        }
        return Map.of("runId", runId, "artifactId", artifactId);
    }

    private static Map<String, Object> runOutput(RunSnapshot run) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("runId", run.runId()); data.put("module", run.module()); data.put("environment", run.environment());
        data.put("headless", run.headless()); data.put("tags", run.tags()); data.put("timeoutSeconds", run.timeoutSeconds());
        data.put("state", run.state().name()); data.put("createdAt", run.createdAt().toString());
        if (run.startedAt() != null) data.put("startedAt", run.startedAt().toString()); if (run.finishedAt() != null) data.put("finishedAt", run.finishedAt().toString());
        if (run.exitCode() != null) data.put("exitCode", run.exitCode()); if (run.reason() != null) data.put("reason", run.reason());
        data.put("stdoutBytes", run.stdoutBytes()); data.put("stderrBytes", run.stderrBytes()); data.put("stdoutTruncated", run.stdoutTruncated()); data.put("stderrTruncated", run.stderrTruncated());
        if (run.skippedTests() != null) data.put("skippedTests", run.skippedTests());
        return Map.copyOf(data);
    }

    private static Map<String, Object> summaryOutput(String runId, SurefireSummary summary) {
        List<Map<String, Object>> suites = summary.suites().stream().map(suite -> Map.<String, Object>of("id", suite.id(), "tests", suite.tests(),
                "failures", suite.failures(), "errors", suite.errors(), "skipped", suite.skipped(), "duration", suite.duration().toPlainString())).toList();
        boolean detailsTruncated = summary.detailsTruncated() || summary.suites().stream().anyMatch(suite -> !suite.testcases().isEmpty());
        return Map.of("runId", runId, "tests", summary.tests(), "passed", summary.passed(), "failures", summary.failures(), "errors", summary.errors(),
                "skipped", summary.skipped(), "duration", summary.duration().toPlainString(), "suites", suites, "detailsTruncated", detailsTruncated);
    }
    private static Map<String, Object> failureSummaryOutput(String runId, SurefireSummary summary) {
        List<Map<String, Object>> records = summary.failureRecords().stream().map(RegressionMcpServer::failureRecordOutput).toList();
        return Map.of("runId", runId, "tests", summary.tests(), "failures", summary.failures(), "errors", summary.errors(),
                "skipped", summary.skipped(), "failureRecords", records, "allureAvailability", summary.allureAvailability(),
                "detailsTruncated", summary.detailsTruncated());
    }
    private static Map<String, Object> failureRecordOutput(SurefireSummary.FailureRecord record) {
        Map<String, Object> allure = new java.util.LinkedHashMap<>();
        allure.put("availability", record.allure().availability());
        if (record.allure().scenario() != null) allure.put("scenario", record.allure().scenario());
        if (record.allure().statusDetails() != null) allure.put("statusDetails", record.allure().statusDetails());
        allure.put("steps", record.allure().steps().stream().map(RegressionMcpServer::stepOutput).toList());
        allure.put("attachmentsPresent", record.allure().attachmentsPresent()); allure.put("truncated", record.allure().truncated());
        return Map.of("failureId", record.failureId(), "type", record.type(), "suite", record.suite(), "testCase", record.testCase(),
                "message", record.message(), "stackTrace", record.stackTrace(), "allure", Map.copyOf(allure), "recordTruncated", record.recordTruncated());
    }
    private static CallToolResult failureSummaryResult(Map<String, Object> data) {
        Map<String, Object> output = Map.of("status", "ok", "data", data);
        try {
            if (serialize(output).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FAILURE_SUMMARY_RESPONSE_BYTES) {
                return errorResult("REPORT_MALFORMED", "The published failure summary exceeds its bounded response contract.");
            }
            return successResult(output);
        } catch (RuntimeException exception) { return errorResult("REPORT_MALFORMED", "The published failure summary cannot be represented safely."); }
    }
    private static Map<String, Object> stepOutput(SurefireSummary.Step step) {
        return Map.of("name", step.name(), "status", step.status(), "steps", step.steps().stream().map(RegressionMcpServer::stepOutput).toList());
    }

    private static Map<String, Object> artifactOutput(FailureArtifact artifact) {
        return Map.of("artifactId", artifact.artifactId(), "name", artifact.name(), "mimeType", artifact.mimeType(),
                "size", artifact.size(), "relativePath", artifact.relativePath());
    }
    private static Map<String, Object> artifactsOutput(String runId, List<FailureArtifact> artifacts) {
        return Map.of("status", "ok", "data", Map.of("runId", runId, "artifacts", artifacts.stream().map(RegressionMcpServer::artifactOutput).toList()));
    }
    private static Map<String, Object> readArtifactOutput(ArtifactContent content) {
        Map<String, Object> artifact = new java.util.LinkedHashMap<>(artifactOutput(content.metadata()));
        artifact.put("content", java.util.Base64.getEncoder().encodeToString(content.content()));
        return Map.copyOf(artifact);
    }
    private static CallToolResult readArtifactResult(Map<String, Object> data) {
        Map<String, Object> output = Map.of("status", "ok", "data", data);
        try {
            if (serialize(output).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_ARTIFACT_READ_RESPONSE_BYTES) {
                return errorResult("ARTIFACT_TOO_LARGE", "The requested artifact exceeds its bounded response contract.");
            }
            return successResult(output);
        } catch (RuntimeException exception) { return errorResult("ARTIFACT_TOO_LARGE", "The requested artifact cannot be represented safely."); }
    }

    private static ToolAnnotations readOnlyAnnotations() {
        return ToolAnnotations.builder()
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(false)
                .build();
    }

    private static ToolAnnotations executionAnnotations(boolean readOnly, boolean destructive, boolean idempotent, boolean openWorld) {
        return ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(destructive).idempotentHint(idempotent).openWorldHint(openWorld).build();
    }

    private static CallToolResult successResult(Map<String, Object> output) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(serialize(output)).build()))
                .structuredContent(output)
                .isError(false)
                .build();
    }

    private static String serialize(Map<String, Object> output) {
        try {
            return McpJsonDefaults.getMapper().writeValueAsString(output);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize tool output.", exception);
        }
    }

    private static CallToolResult moduleErrorResult(String code, String message) {
        Map<String, Object> output = Map.of(
                "status", "error",
                "error", Map.of("code", code, "message", message));
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(serialize(output)).build()))
                .structuredContent(output)
                .isError(true)
                .build();
    }

    private static CallToolResult errorResult(String code, String message) {
        return moduleErrorResult(code, message);
    }
}
