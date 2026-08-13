package com.aqa.mcp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
        McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(INSTRUCTIONS)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(overviewTool(repositoryRoot), listModulesTool(repositoryRoot), featureListTool(repositoryRoot), scenarioListTool(repositoryRoot))
                .build();
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
                        return errorResult("This tool does not accept arguments.");
                    }
                    try {
                        Map<String, Object> output = FrameworkOverview.forRoot(
                                RepositoryRootResolver.resolve(repositoryRoot.path())).asToolOutput();
                        return successResult(output);
                    }
                    catch (IllegalArgumentException exception) {
                        return errorResult(exception.getMessage());
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

    private static Map<String, Object> moduleInputSchema(boolean allowTags) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("module", Map.of("type", "string"));
        if (allowTags) properties.put("tags", Map.of("type", "string"));
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("module"), "properties", properties);
    }

    private static Map<String, Object> featureOutputSchema() {
        Map<String, Object> feature = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("name", "language", "tags", "path", "line", "scenarioCount"), "properties", Map.of(
                        "name", Map.of("type", "string"), "language", Map.of("type", "string"), "tags", stringArray(),
                        "path", Map.of("type", "string"), "line", Map.of("type", "integer"), "scenarioCount", Map.of("type", "integer")));
        return discoverySchema(Map.of("module", Map.of("type", "string"), "featureRoot", Map.of("type", "string"),
                "featureRootExists", Map.of("type", "boolean"), "features", Map.of("type", "array", "items", feature)),
                List.of("module", "featureRoot", "featureRootExists", "features"));
    }

    private static Map<String, Object> scenarioOutputSchema() {
        Map<String, Object> scenario = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("feature", "name", "type", "tags", "path", "line"), "properties", Map.of(
                        "feature", Map.of("type", "string"), "name", Map.of("type", "string"), "type", Map.of("type", "string"),
                        "tags", stringArray(), "path", Map.of("type", "string"), "line", Map.of("type", "integer")));
        return discoverySchema(Map.of("module", Map.of("type", "string"), "scenarios", Map.of("type", "array", "items", scenario)),
                List.of("module", "scenarios"));
    }

    private static Map<String, Object> discoverySchema(Map<String, Object> dataProperties, List<String> requiredData) {
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
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("status", "data"),
                "properties", Map.of(
                        "status", Map.of("type", "string", "const", "ok"),
                        "data", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("name", "root", "javaVersion", "buildTool", "availability"),
                                "properties", dataProperties)));
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
        Map<String, Object> successSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("status", "data"),
                "properties", Map.of(
                        "status", Map.of("type", "string", "const", "ok"),
                        "data", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("modules"),
                                "properties", Map.of("modules", Map.of("type", "array", "items", moduleSchema)))));
        Map<String, Object> errorSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("status", "error"),
                "properties", Map.of(
                        "status", Map.of("type", "string", "const", "error"),
                        "error", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("code", "message"),
                                "properties", Map.of(
                                        "code", Map.of("type", "string"),
                                        "message", Map.of("type", "string")))));
        return Map.of("oneOf", List.of(successSchema, errorSchema));
    }

    private static ToolAnnotations readOnlyAnnotations() {
        return ToolAnnotations.builder()
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(false)
                .build();
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

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(message).build()))
                .isError(true)
                .build();
    }
}
