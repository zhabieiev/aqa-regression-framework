package com.aqa.mcp.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * MCP tool wiring for regression_validate_framework_conventions. Self-contained and structured exactly like
 * ModuleBoundariesTool from Gate 15.3: builds its own schema and result envelopes, and resolves
 * moduleTypeByNameSupplier once per request inside callHandler (never at server-registration time), matching the
 * per-request-resolution behavior every other read-only tool already follows.
 *
 * The one shape difference from ModuleBoundariesTool: FC-004 (records-for-value-objects) is an explicitly
 * advisory/non-blocking rule (accepted in Gate 15.1, reconfirmed in Gate 15.4). Rather than adding a severity
 * concept to the shared Violation record used by MOD-001..004 and ModuleBoundariesTool (out of this gate's
 * authorized scope), this tool partitions its own output only: violations carrying FC-004's rule id are reported in
 * a separate "advisoryViolations" array instead of the blocking "violations" array.
 */
public final class FrameworkConventionsTool {

    public static final String TOOL_NAME = "regression_validate_framework_conventions";
    private static final String DESCRIPTION = "Checks declared reactor module source against the fixed "
            + "framework-convention rules (Selenium/Playwright locator discipline, no blocking waits, constructor "
            + "injection, static mutable UI state) in the repository's framework-convention policy.";

    private FrameworkConventionsTool() {
    }

    public static SyncToolSpecification tool(Path repositoryRoot, Supplier<Map<String, String>> moduleTypeByNameSupplier) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(TOOL_NAME, inputSchema())
                        .description(DESCRIPTION)
                        .annotations(readOnlyAnnotations())
                        .outputSchema(outputSchema())
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        return successResult(reportOutput(evaluate(repositoryRoot, moduleTypeByNameSupplier.get(), request.arguments())));
                    }
                    catch (ValidationException exception) {
                        return errorResult(exception.code(), exception.getMessage());
                    }
                })
                .build();
    }

    private static ValidationReport evaluate(Path repositoryRoot, Map<String, String> moduleTypeByName, Map<String, Object> arguments) {
        List<String> declaredModules = List.copyOf(moduleTypeByName.keySet());
        ValidationScopeRequest scopeRequest = parseRequest(arguments);
        ValidatedValidationScope scope = ValidationScopeValidator.validate(scopeRequest, declaredModules, moduleTypeByName);

        Map<String, List<SourceUnit>> sourcesByModule = new LinkedHashMap<>();
        for (String module : declaredModules) {
            sourcesByModule.put(module, JavaSourceScanner.scan(repositoryRoot, module));
        }
        List<ModuleProfile> reactorModules = declaredModules.stream()
                .map(module -> new ModuleProfile(module, RuleProfileResolver.resolve(moduleTypeByName.get(module)),
                        BasePackages.derive(sourcesByModule.get(module))))
                .toList();

        List<ValidationRule> rules = FrameworkConventionRules.all();
        List<ModuleValidationResult> results = new ArrayList<>();
        for (String module : scope.modules()) {
            RuleProfile profile = RuleProfileResolver.resolve(moduleTypeByName.get(module));
            EvaluationContext context = new EvaluationContext(module, profile, sourcesByModule.get(module), reactorModules);
            List<String> rulesApplied = new ArrayList<>();
            List<Violation> violations = new ArrayList<>();
            for (ValidationRule rule : rules) {
                if (rule.profiles().contains(profile)) {
                    rulesApplied.add(rule.id());
                    violations.addAll(rule.evaluate(context));
                }
            }
            results.add(new ModuleValidationResult(module, profile, List.copyOf(rulesApplied), List.copyOf(violations), false));
        }
        return new ValidationReport(List.copyOf(results));
    }

    private static ValidationScopeRequest parseRequest(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new ValidationScopeRequest(null, null);
        }
        if (!arguments.keySet().stream().allMatch(key -> key.equals("module") || key.equals("profile"))) {
            throw new ValidationException("INVALID_ARGUMENTS", "Only module and profile are accepted.");
        }
        Object module = arguments.get("module");
        Object profile = arguments.get("profile");
        if (module != null && !(module instanceof String) || profile != null && !(profile instanceof String)) {
            throw new ValidationException("INVALID_ARGUMENTS", "module and profile must be strings.");
        }
        return new ValidationScopeRequest((String) module, (String) profile);
    }

    private static Map<String, Object> reportOutput(ValidationReport report) {
        return Map.of("modules", report.modules().stream().map(FrameworkConventionsTool::moduleResultOutput).toList());
    }

    private static Map<String, Object> moduleResultOutput(ModuleValidationResult result) {
        List<Map<String, Object>> violations = new ArrayList<>();
        List<Map<String, Object>> advisoryViolations = new ArrayList<>();
        for (Violation violation : result.violations()) {
            Map<String, Object> output = violationOutput(violation);
            if (FrameworkConventionRules.RECORDS_FOR_VALUE_OBJECTS_RULE_ID.equals(violation.ruleId())) {
                advisoryViolations.add(output);
            }
            else {
                violations.add(output);
            }
        }
        return Map.of("module", result.module(), "profile", result.profile().name(), "rulesApplied", result.rulesApplied(),
                "violations", List.copyOf(violations), "advisoryViolations", List.copyOf(advisoryViolations), "truncated", result.truncated());
    }

    private static Map<String, Object> violationOutput(Violation violation) {
        return Map.of("ruleId", violation.ruleId(), "module", violation.module(), "file", violation.file(),
                "line", violation.line(), "message", violation.message());
    }

    private static Map<String, Object> inputSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("module", Map.of("type", "string"),
                        "profile", Map.of("type", "string", "enum", RuleProfile.schemaValues())));
    }

    private static Map<String, Object> violationSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("ruleId", "module", "file", "line", "message"),
                "properties", Map.of("ruleId", Map.of("type", "string"), "module", Map.of("type", "string"),
                        "file", Map.of("type", "string"), "line", Map.of("type", "integer"), "message", Map.of("type", "string")));
    }

    private static Map<String, Object> moduleResultSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("module", "profile", "rulesApplied", "violations", "advisoryViolations", "truncated"),
                "properties", Map.of("module", Map.of("type", "string"),
                        "profile", Map.of("type", "string", "enum", RuleProfile.schemaValues()),
                        "rulesApplied", Map.of("type", "array", "items", Map.of("type", "string")),
                        "violations", Map.of("type", "array", "items", violationSchema()),
                        "advisoryViolations", Map.of("type", "array", "items", violationSchema()),
                        "truncated", Map.of("type", "boolean")));
    }

    private static Map<String, Object> outputSchema() {
        Map<String, Object> success = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("status", "data"),
                "properties", Map.of("status", Map.of("type", "string", "const", "ok"),
                        "data", Map.of("type", "object", "additionalProperties", false, "required", List.of("modules"),
                                "properties", Map.of("modules", Map.of("type", "array", "items", moduleResultSchema())))));
        Map<String, Object> failure = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("status", "error"),
                "properties", Map.of("status", Map.of("type", "string", "const", "error"),
                        "error", Map.of("type", "object", "additionalProperties", false,
                                "required", List.of("code", "message"),
                                "properties", Map.of("code", Map.of("type", "string"), "message", Map.of("type", "string")))));
        return Map.of("oneOf", List.of(success, failure));
    }

    private static ToolAnnotations readOnlyAnnotations() {
        return ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).idempotentHint(true).openWorldHint(false).build();
    }

    private static CallToolResult successResult(Map<String, Object> data) {
        Map<String, Object> output = Map.of("status", "ok", "data", data);
        return CallToolResult.builder().content(List.of(TextContent.builder(serialize(output)).build()))
                .structuredContent(output).isError(false).build();
    }

    private static CallToolResult errorResult(String code, String message) {
        Map<String, Object> output = Map.of("status", "error", "error", Map.of("code", code, "message", message));
        return CallToolResult.builder().content(List.of(TextContent.builder(serialize(output)).build()))
                .structuredContent(output).isError(true).build();
    }

    private static String serialize(Map<String, Object> output) {
        try {
            return McpJsonDefaults.getMapper().writeValueAsString(output);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize tool output.", exception);
        }
    }
}
