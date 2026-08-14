package com.aqa.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aqa.mcp.execution.CloseAwareInputStream;
import com.aqa.mcp.execution.ExecutionPlanningException;
import com.aqa.mcp.execution.RunSnapshot;
import com.aqa.mcp.execution.StartTestRunRequest;
import com.aqa.mcp.execution.TestRunCoordinator;
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

    static final String SERVER_NAME = "regression-mcp-server";
    static final String SERVER_VERSION = "1.0.0";
    static final String OVERVIEW_TOOL_NAME = "regression_get_framework_overview";
    static final String LIST_MODULES_TOOL_NAME = "regression_list_modules";
    static final String LIST_FEATURES_TOOL_NAME = "regression_list_features";
    static final String LIST_SCENARIOS_TOOL_NAME = "regression_list_scenarios";
    static final String START_TEST_RUN_TOOL_NAME = "regression_start_test_run";
    static final String GET_TEST_RUN_TOOL_NAME = "regression_get_test_run";
    static final String CANCEL_TEST_RUN_TOOL_NAME = "regression_cancel_test_run";
    private static final String INSTRUCTIONS =
            "This is a local, read-only framework inspection server. It exposes only deterministic inspection "
                    + "tools for the repository configured by REGRESSION_ROOT.";

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
                        startTestRunTool(coordinator), getTestRunTool(coordinator), cancelTestRunTool(coordinator))
                .build();
    }

    static SyncToolSpecification startTestRunTool(TestRunCoordinator coordinator) {
        return SyncToolSpecification.builder().tool(Tool.builder(START_TEST_RUN_TOOL_NAME, startInputSchema())
                .description("Starts the single allowed Commerce test run.").annotations(executionAnnotations(false, true, false, true)).outputSchema(runOutputSchema()).build())
                .callHandler((exchange, request) -> { try { return successResult(Map.of("status", "ok", "data", runOutput(coordinator.start(startRequest(request.arguments()), System.getenv())))); }
                    catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); } }).build();
    }

    static SyncToolSpecification getTestRunTool(TestRunCoordinator coordinator) { return runActionTool(GET_TEST_RUN_TOOL_NAME, coordinator, true); }
    static SyncToolSpecification cancelTestRunTool(TestRunCoordinator coordinator) { return runActionTool(CANCEL_TEST_RUN_TOOL_NAME, coordinator, false); }
    private static SyncToolSpecification runActionTool(String name, TestRunCoordinator coordinator, boolean get) {
        return SyncToolSpecification.builder().tool(Tool.builder(name, runIdInputSchema()).description("Returns or cancels a server-generated test run.")
                .annotations(get ? executionAnnotations(true, false, true, false) : executionAnnotations(false, true, true, false)).outputSchema(runOutputSchema()).build()).callHandler((exchange, request) -> { try {
                    String id = runId(request.arguments()); RunSnapshot snapshot = get ? coordinator.get(id) : coordinator.cancel(id);
                    return successResult(Map.of("status", "ok", "data", runOutput(snapshot))); } catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); } }).build();
    }

    static SyncToolSpecification overviewTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(OVERVIEW_TOOL_NAME, inputSchema())
                        .description("Returns a deterministic overview of the local regression framework.")
                        .annotations(readOnlyAnnotations())
                        .outputSchema(outputSchema())
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
                .tool(Tool.builder(LIST_MODULES_TOOL_NAME, inputSchema())
                        .description("Lists the reactor modules declared by the root parent pom.xml.")
                        .annotations(readOnlyAnnotations())
                        .outputSchema(moduleListOutputSchema())
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
                .tool(Tool.builder(LIST_FEATURES_TOOL_NAME, moduleInputSchema(false))
                        .description("Lists parsed Gherkin features below a declared module's feature root.")
                        .annotations(readOnlyAnnotations()).outputSchema(featureOutputSchema()).build())
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
                .tool(Tool.builder(LIST_SCENARIOS_TOOL_NAME, moduleInputSchema(true))
                        .description("Lists executable Cucumber scenarios below a declared module's feature root.")
                        .annotations(readOnlyAnnotations()).outputSchema(scenarioOutputSchema()).build())
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
                        return moduleErrorResult(exception instanceof RepositoryInspectionException inspection ? inspection.code() : "INVALID_ARGUMENTS", exception.getMessage());
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

    private static Map<String, Object> runOutput(RunSnapshot run) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("runId", run.runId()); data.put("module", run.module()); data.put("environment", run.environment());
        data.put("headless", run.headless()); data.put("tags", run.tags()); data.put("timeoutSeconds", run.timeoutSeconds());
        data.put("state", run.state().name()); data.put("createdAt", run.createdAt().toString());
        if (run.startedAt() != null) data.put("startedAt", run.startedAt().toString()); if (run.finishedAt() != null) data.put("finishedAt", run.finishedAt().toString());
        if (run.exitCode() != null) data.put("exitCode", run.exitCode()); if (run.reason() != null) data.put("reason", run.reason());
        data.put("stdoutBytes", run.stdoutBytes()); data.put("stderrBytes", run.stderrBytes()); data.put("stdoutTruncated", run.stdoutTruncated()); data.put("stderrTruncated", run.stderrTruncated());
        return Map.copyOf(data);
    }

    private static Map<String, Object> moduleInputSchema(boolean allowTags) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("module", Map.of("type", "string"));
        if (allowTags) properties.put("tags", Map.of("type", "string"));
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("module"), "properties", properties);
    }

    private static Map<String, Object> startInputSchema() { return Map.of("type", "object", "additionalProperties", false,
            "required", List.of("module", "environment", "headless", "timeoutSeconds"), "properties", Map.of("module", Map.of("type", "string"), "tags", Map.of("type", "string", "maxLength", 1024), "environment", Map.of("type", "string"), "headless", Map.of("type", "boolean"), "timeoutSeconds", Map.of("type", "integer"))); }
    private static Map<String, Object> runIdInputSchema() { return Map.of("type", "object", "additionalProperties", false, "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))); }
    private static Map<String, Object> runOutputSchema() { return structuredOutputSchema(Map.ofEntries(Map.entry("runId", Map.of("type", "string")), Map.entry("module", Map.of("type", "string")), Map.entry("environment", Map.of("type", "string")), Map.entry("headless", Map.of("type", "boolean")), Map.entry("tags", Map.of("type", "string")), Map.entry("timeoutSeconds", Map.of("type", "integer")), Map.entry("state", Map.of("type", "string")), Map.entry("createdAt", Map.of("type", "string")), Map.entry("startedAt", Map.of("type", "string")), Map.entry("finishedAt", Map.of("type", "string")), Map.entry("exitCode", Map.of("type", "integer")), Map.entry("reason", Map.of("type", "string")), Map.entry("stdoutBytes", Map.of("type", "integer")), Map.entry("stderrBytes", Map.of("type", "integer")), Map.entry("stdoutTruncated", Map.of("type", "boolean")), Map.entry("stderrTruncated", Map.of("type", "boolean"))), List.of("runId", "module", "environment", "headless", "tags", "timeoutSeconds", "state", "createdAt", "stdoutBytes", "stderrBytes", "stdoutTruncated", "stderrTruncated")); }

    private static Map<String, Object> featureOutputSchema() {
        Map<String, Object> feature = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("name", "language", "tags", "path", "line", "scenarioCount"), "properties", Map.of(
                        "name", Map.of("type", "string"), "language", Map.of("type", "string"), "tags", stringArray(),
                        "path", Map.of("type", "string"), "line", Map.of("type", "integer"), "scenarioCount", Map.of("type", "integer")));
        return structuredOutputSchema(Map.of("module", Map.of("type", "string"), "featureRoot", Map.of("type", "string"),
                "featureRootExists", Map.of("type", "boolean"), "features", Map.of("type", "array", "items", feature)),
                List.of("module", "featureRoot", "featureRootExists", "features"));
    }

    private static Map<String, Object> scenarioOutputSchema() {
        Map<String, Object> scenario = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("feature", "name", "type", "tags", "path", "line"), "properties", Map.of(
                        "feature", Map.of("type", "string"), "name", Map.of("type", "string"), "type", Map.of("type", "string"),
                        "tags", stringArray(), "path", Map.of("type", "string"), "line", Map.of("type", "integer")));
        return structuredOutputSchema(Map.of("module", Map.of("type", "string"), "scenarios", Map.of("type", "array", "items", scenario)),
                List.of("module", "scenarios"));
    }

    private static Map<String, Object> structuredOutputSchema(Map<String, Object> dataProperties, List<String> requiredData) {
        Map<String, Object> success = Map.of("type", "object", "additionalProperties", false, "required", List.of("status", "data"),
                "properties", Map.of("status", Map.of("type", "string", "const", "ok"), "data", Map.of("type", "object",
                        "additionalProperties", false, "required", requiredData, "properties", dataProperties)));
        Map<String, Object> failure = Map.of("type", "object", "additionalProperties", false, "required", List.of("status", "error"),
                "properties", Map.of("status", Map.of("type", "string", "const", "error"), "error", Map.of("type", "object",
                        "additionalProperties", false, "required", List.of("code", "message"), "properties", Map.of(
                                "code", Map.of("type", "string"), "message", Map.of("type", "string")))));
        return Map.of("oneOf", List.of(success, failure));
    }

    private static Map<String, Object> stringArray() { return Map.of("type", "array", "items", Map.of("type", "string")); }
    static Map<String, Object> inputSchema() {
        return Map.of("type", "object", "additionalProperties", false);
    }

    static Map<String, Object> outputSchema() {
        Map<String, Object> dataProperties = Map.of(
                "name", Map.of("type", "string"),
                "root", Map.of("type", "string"),
                "javaVersion", Map.of("type", "string"),
                "buildTool", Map.of("type", "string"),
                "availability", Map.of("type", "string"));
        return structuredOutputSchema(dataProperties,
                List.of("name", "root", "javaVersion", "buildTool", "availability"));
    }

    static Map<String, Object> moduleListOutputSchema() {
        Map<String, Object> moduleProperties = Map.of(
                "name", Map.of("type", "string"),
                "relativePath", Map.of("type", "string"),
                "type", Map.of("type", "string", "enum", ModuleType.schemaValues()),
                "directoryExists", Map.of("type", "boolean"),
                "pomExists", Map.of("type", "boolean"));
        Map<String, Object> moduleSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("name", "relativePath", "type", "directoryExists", "pomExists"),
                "properties", moduleProperties);
        return structuredOutputSchema(Map.of("modules", Map.of("type", "array", "items", moduleSchema)),
                List.of("modules"));
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
