package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schema/contract-level test for the new tool's registration shape, mirroring
 * com.aqa.mcp.RegressionMcpServerContractTest's style but exercising ModuleBoundariesTool.tool(...) directly
 * (rather than the full RegressionMcpServer.createServer(...) registration) because RegressionMcpServer.java and
 * its own existing contract test are the only pre-existing com.aqa.mcp files this gate is authorized to touch, and
 * every field this contract cares about (name, input schema, output schema, annotations) is fully decided by
 * ModuleBoundariesTool itself.
 */
class ModuleBoundariesToolContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTheClosedModuleAndProfileFilterInputContract() {
        SyncToolSpecification tool = ModuleBoundariesTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().name()).isEqualTo(ModuleBoundariesTool.TOOL_NAME).isEqualTo("regression_validate_module_boundaries");
        assertThat(tool.tool().inputSchema()).isEqualTo(Map.of(
                "type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "module", Map.of("type", "string"),
                        "profile", Map.of("type", "string", "enum", List.of("CORE", "API", "UI", "API_UI", "MCP", "TEST_ONLY")))));
    }

    @Test
    void exposesAStructuredOneOfOutputContract() {
        SyncToolSpecification tool = ModuleBoundariesTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().outputSchema()).containsKey("oneOf");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) tool.tool().outputSchema().get("oneOf");
        assertThat(branches).hasSize(2);
    }

    @Test
    void exposesReadOnlyAnnotations() {
        SyncToolSpecification tool = ModuleBoundariesTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().annotations().readOnlyHint()).isTrue();
        assertThat(tool.tool().annotations().destructiveHint()).isFalse();
        assertThat(tool.tool().annotations().idempotentHint()).isTrue();
        assertThat(tool.tool().annotations().openWorldHint()).isFalse();
    }
}
