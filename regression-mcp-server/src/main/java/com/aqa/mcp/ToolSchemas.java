package com.aqa.mcp;

import java.util.List;
import java.util.Map;

/**
 * Builders for the closed JSON Schema documents attached to every MCP tool this server registers.
 *
 * <p>Each method returns a fresh {@code Map<String, Object>} describing one tool's {@code inputSchema},
 * one tool's {@code outputSchema}, or a reusable schema fragment. The maps are handed to
 * {@code io.modelcontextprotocol.spec.McpSchema.Tool.Builder} by {@code RegressionMcpServer}'s tool
 * factory methods and are serialized verbatim into the {@code tools/list} JSON-RPC response, so a
 * change to any body is a change to the client-facing contract.
 */
final class ToolSchemas {

    private ToolSchemas() {
    }

    static Map<String, Object> moduleInputSchema(boolean allowTags) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("module", Map.of("type", "string"));
        if (allowTags) properties.put("tags", Map.of("type", "string"));
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("module"), "properties", properties);
    }

    static Map<String, Object> startInputSchema() { return Map.of("type", "object", "additionalProperties", false,
            "required", List.of("module", "environment", "headless", "timeoutSeconds"), "properties", Map.of("module", Map.of("type", "string"), "tags", Map.of("type", "string", "maxLength", 1024), "environment", Map.of("type", "string"), "headless", Map.of("type", "boolean"), "timeoutSeconds", Map.of("type", "integer"))); }
    static Map<String, Object> runIdInputSchema() { return Map.of("type", "object", "additionalProperties", false, "required", List.of("runId"), "properties", Map.of("runId", Map.of("type", "string"))); }
    static Map<String, Object> runOutputSchema() { return structuredOutputSchema(Map.ofEntries(Map.entry("runId", Map.of("type", "string")), Map.entry("module", Map.of("type", "string")), Map.entry("environment", Map.of("type", "string")), Map.entry("headless", Map.of("type", "boolean")), Map.entry("tags", Map.of("type", "string")), Map.entry("timeoutSeconds", Map.of("type", "integer")), Map.entry("state", Map.of("type", "string")), Map.entry("createdAt", Map.of("type", "string")), Map.entry("startedAt", Map.of("type", "string")), Map.entry("finishedAt", Map.of("type", "string")), Map.entry("exitCode", Map.of("type", "integer")), Map.entry("reason", Map.of("type", "string")), Map.entry("stdoutBytes", Map.of("type", "integer")), Map.entry("stderrBytes", Map.of("type", "integer")), Map.entry("stdoutTruncated", Map.of("type", "boolean")), Map.entry("stderrTruncated", Map.of("type", "boolean")), Map.entry("skippedTests", Map.of("type", "integer"))), List.of("runId", "module", "environment", "headless", "tags", "timeoutSeconds", "state", "createdAt", "stdoutBytes", "stderrBytes", "stdoutTruncated", "stderrTruncated")); }
    static Map<String, Object> summaryOutputSchema() {
        Map<String, Object> suite = Map.of("type", "object", "additionalProperties", false, "required", List.of("id", "tests", "failures", "errors", "skipped", "duration"),
                "properties", Map.of("id", Map.of("type", "string"), "tests", Map.of("type", "integer"), "failures", Map.of("type", "integer"),
                        "errors", Map.of("type", "integer"), "skipped", Map.of("type", "integer"), "duration", Map.of("type", "string")));
        return structuredOutputSchema(Map.of("runId", Map.of("type", "string"), "tests", Map.of("type", "integer"), "passed", Map.of("type", "integer"),
                "failures", Map.of("type", "integer"), "errors", Map.of("type", "integer"), "skipped", Map.of("type", "integer"),
                "duration", Map.of("type", "string"), "suites", Map.of("type", "array", "items", suite), "detailsTruncated", Map.of("type", "boolean")),
                List.of("runId", "tests", "passed", "failures", "errors", "skipped", "duration", "suites", "detailsTruncated"));
    }
    static Map<String, Object> failureSummaryOutputSchema() {
        Map<String, Object> step = Map.of("type", "object", "additionalProperties", false, "required", List.of("name", "status", "steps"),
                "properties", Map.of("name", Map.of("type", "string"), "status", Map.of("type", "string"), "steps", Map.of("type", "array")));
        Map<String, Object> allure = Map.of("type", "object", "additionalProperties", false, "required", List.of("availability", "steps", "attachmentsPresent", "truncated"),
                "properties", Map.of("availability", Map.of("type", "string"), "scenario", Map.of("type", "string"), "statusDetails", Map.of("type", "string"),
                        "steps", Map.of("type", "array", "items", step), "attachmentsPresent", Map.of("type", "boolean"), "truncated", Map.of("type", "boolean")));
        Map<String, Object> record = Map.of("type", "object", "additionalProperties", false, "required", List.of("failureId", "type", "suite", "testCase", "message", "stackTrace", "allure", "recordTruncated"),
                "properties", Map.of("failureId", Map.of("type", "string"), "type", Map.of("type", "string"), "suite", Map.of("type", "string"), "testCase", Map.of("type", "string"),
                        "message", Map.of("type", "string"), "stackTrace", Map.of("type", "string"), "allure", allure, "recordTruncated", Map.of("type", "boolean")));
        return structuredOutputSchema(Map.of("runId", Map.of("type", "string"), "tests", Map.of("type", "integer"), "failures", Map.of("type", "integer"),
                "errors", Map.of("type", "integer"), "skipped", Map.of("type", "integer"), "failureRecords", Map.of("type", "array", "items", record),
                "allureAvailability", Map.of("type", "string"), "detailsTruncated", Map.of("type", "boolean")),
                List.of("runId", "tests", "failures", "errors", "skipped", "failureRecords", "allureAvailability", "detailsTruncated"));
    }

    private static Map<String, Object> artifactSchemaProperties() {
        return Map.of("artifactId", Map.of("type", "string"), "name", Map.of("type", "string"), "mimeType", Map.of("type", "string"),
                "size", Map.of("type", "integer"), "relativePath", Map.of("type", "string"));
    }
    private static Map<String, Object> artifactSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("artifactId", "name", "mimeType", "size", "relativePath"), "properties", artifactSchemaProperties());
    }
    static Map<String, Object> artifactsOutputSchema() {
        return structuredOutputSchema(Map.of("runId", Map.of("type", "string"), "artifacts", Map.of("type", "array", "items", artifactSchema())),
                List.of("runId", "artifacts"));
    }
    static Map<String, Object> readArtifactOutputSchema() {
        Map<String, Object> properties = new java.util.LinkedHashMap<>(artifactSchemaProperties());
        properties.put("content", Map.of("type", "string"));
        return structuredOutputSchema(Map.copyOf(properties),
                List.of("artifactId", "name", "mimeType", "size", "relativePath", "content"));
    }
    static Map<String, Object> artifactInputSchema() {
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("runId", "artifactId"),
                "properties", Map.of("runId", Map.of("type", "string"), "artifactId", Map.of("type", "string")));
    }

    static Map<String, Object> featureOutputSchema() {
        Map<String, Object> feature = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("name", "language", "tags", "path", "line", "scenarioCount"), "properties", Map.of(
                        "name", Map.of("type", "string"), "language", Map.of("type", "string"), "tags", stringArray(),
                        "path", Map.of("type", "string"), "line", Map.of("type", "integer"), "scenarioCount", Map.of("type", "integer")));
        return structuredOutputSchema(Map.of("module", Map.of("type", "string"), "featureRoot", Map.of("type", "string"),
                "featureRootExists", Map.of("type", "boolean"), "features", Map.of("type", "array", "items", feature)),
                List.of("module", "featureRoot", "featureRootExists", "features"));
    }

    static Map<String, Object> scenarioOutputSchema() {
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
}
