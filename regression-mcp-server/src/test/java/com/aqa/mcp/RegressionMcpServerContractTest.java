package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
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
        assertThat(toolSpecification.tool().outputSchema())
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false)
                .containsEntry("required", List.of("status", "data"))
                .containsKey("properties");
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
