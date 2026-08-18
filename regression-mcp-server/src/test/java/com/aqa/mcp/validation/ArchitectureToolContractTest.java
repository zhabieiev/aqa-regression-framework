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
 * ModuleBoundariesToolContractTest's and FrameworkConventionsToolContractTest's style from Gates 15.3/15.4: it
 * exercises ArchitectureTool.tool(...) directly rather than the full RegressionMcpServer.createServer(...)
 * registration, since every field this contract cares about (name, input schema, output schema, annotations) is
 * fully decided by ArchitectureTool itself.
 */
class ArchitectureToolContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTheClosedModuleAndProfileFilterInputContract() {
        SyncToolSpecification tool = ArchitectureTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().name()).isEqualTo(ArchitectureTool.TOOL_NAME).isEqualTo("regression_validate_architecture");
        assertThat(tool.tool().inputSchema()).isEqualTo(Map.of(
                "type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "module", Map.of("type", "string"),
                        "profile", Map.of("type", "string", "enum", List.of("CORE", "API", "UI", "API_UI", "MCP", "TEST_ONLY")))));
    }

    @Test
    void exposesAStructuredOneOfOutputContractWithAnAdvisoryViolationsField() {
        SyncToolSpecification tool = ArchitectureTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().outputSchema()).containsKey("oneOf");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) tool.tool().outputSchema().get("oneOf");
        assertThat(branches).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> success = branches.getFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) success.get("properties")).get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> modules = (Map<String, Object>) ((Map<String, Object>) data.get("properties")).get("modules");
        @SuppressWarnings("unchecked")
        Map<String, Object> moduleResult = (Map<String, Object>) modules.get("items");
        assertThat((List<String>) moduleResult.get("required")).contains("violations", "advisoryViolations");
    }

    @Test
    void exposesReadOnlyAnnotations() {
        SyncToolSpecification tool = ArchitectureTool.tool(temporaryDirectory, Map::of);

        assertThat(tool.tool().annotations().readOnlyHint()).isTrue();
        assertThat(tool.tool().annotations().destructiveHint()).isFalse();
        assertThat(tool.tool().annotations().idempotentHint()).isTrue();
        assertThat(tool.tool().annotations().openWorldHint()).isFalse();
    }
}
