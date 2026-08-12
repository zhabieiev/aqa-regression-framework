package com.aqa.mcp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    static final String TOOL_NAME = "regression_get_framework_overview";
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
                .tools(overviewTool(repositoryRoot))
                .build();
    }

    static SyncToolSpecification overviewTool(RepositoryRoot repositoryRoot) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(TOOL_NAME, inputSchema())
                        .description("Returns a deterministic overview of the local regression framework.")
                        .annotations(ToolAnnotations.builder()
                                .readOnlyHint(true)
                                .destructiveHint(false)
                                .idempotentHint(true)
                                .openWorldHint(false)
                                .build())
                        .outputSchema(outputSchema())
                        .build())
                .callHandler((exchange, request) -> {
                    if (request.arguments() != null && !request.arguments().isEmpty()) {
                        return errorResult("This tool does not accept arguments.");
                    }

                    try {
                        FrameworkOverview overview = FrameworkOverview.forRoot(
                                RepositoryRootResolver.resolve(repositoryRoot.path()));
                        Map<String, Object> output = overview.asToolOutput();
                        return CallToolResult.builder()
                                .content(List.of(TextContent.builder(serialize(output)).build()))
                                .structuredContent(output)
                                .isError(false)
                                .build();
                    }
                    catch (IllegalArgumentException exception) {
                        return errorResult(exception.getMessage());
                    }
                })
                .build();
    }

    static Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false);
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

    private static String serialize(Map<String, Object> output) {
        try {
            return McpJsonDefaults.getMapper().writeValueAsString(output);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize tool output.", exception);
        }
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(message).build()))
                .isError(true)
                .build();
    }
}
