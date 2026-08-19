package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tier 2: TempDir-based, end-to-end tool coverage — mirrors ModuleBoundariesToolTest's shape. */
class FrameworkConventionsToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsARealFc001ViolationWhenNoFilterIsGiven() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of());

        Map<String, Object> commerce = moduleResult(data, "regression-nextjs-commerce");
        assertThat(rulesApplied(commerce)).contains("FC-001", "FC-001-PW", "FC-002", "FC-002-PW", "FC-003", "FC-004", "FC-005");
        List<Map<String, Object>> violations = violations(commerce);
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().get("ruleId")).isEqualTo("FC-001");

        assertThat(violations(moduleResult(data, "regression-jhipster"))).isEmpty();
        assertThat(advisoryViolations(moduleResult(data, "regression-jhipster"))).hasSize(1);
        assertThat(advisoryViolations(moduleResult(data, "regression-jhipster")).getFirst().get("ruleId")).isEqualTo("FC-004");
    }

    @Test
    void reportsNoViolationsForAModuleWithNoConventionBreaches() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of("module", "regression-core"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(violations(modules.getFirst())).isEmpty();
        assertThat(advisoryViolations(modules.getFirst())).isEmpty();
    }

    @Test
    void filtersByModuleAndProfileTogether() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of("module", "regression-jhipster", "profile", "API_UI"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(modules.getFirst().get("module")).isEqualTo("regression-jhipster");
        assertThat(modules.getFirst().get("profile")).isEqualTo("API_UI");
    }

    @Test
    void rejectsAnUnknownModuleArgument() throws Exception {
        reactor();

        CallToolResult result = invoke(Map.of("module", "not-a-real-module"));

        assertThat(result.isError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        assertThat(structured.get("status")).isEqualTo("error");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) structured.get("error");
        assertThat(error.get("code")).isEqualTo("UNKNOWN_MODULE");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> moduleResult(Map<String, Object> data, String module) {
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        return modules.stream().filter(candidate -> candidate.get("module").equals(module)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> violations(Map<String, Object> moduleResult) {
        return (List<Map<String, Object>>) moduleResult.get("violations");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> advisoryViolations(Map<String, Object> moduleResult) {
        return (List<Map<String, Object>>) moduleResult.get("advisoryViolations");
    }

    @SuppressWarnings("unchecked")
    private static List<String> rulesApplied(Map<String, Object> moduleResult) {
        return (List<String>) moduleResult.get("rulesApplied");
    }

    private Map<String, Object> call(Map<String, Object> arguments) {
        CallToolResult result = invoke(arguments);
        assertThat(result.isError()).as("expected a successful result: %s", result.structuredContent()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) structured.get("data");
        return data;
    }

    private CallToolResult invoke(Map<String, Object> arguments) {
        SyncToolSpecification tool = FrameworkConventionsTool.tool(temporaryDirectory, this::moduleTypeByName);
        return tool.callHandler().apply(null, new CallToolRequest(FrameworkConventionsTool.TOOL_NAME, arguments));
    }

    private Map<String, String> moduleTypeByName() {
        return Map.of("regression-core", "CORE", "regression-jhipster", "API_UI", "regression-nextjs-commerce", "UI");
    }

    /** A minimal, real 3-module reactor: regression-nextjs-commerce carries a genuine FC-001 violation (a
     * non-static-final By field), regression-jhipster carries a genuine FC-004 advisory finding (a plain value
     * class not declared as a record), and regression-core stays clean. */
    private void reactor() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>"
                + "<module>regression-core</module><module>regression-jhipster</module><module>regression-nextjs-commerce</module>"
                + "</modules></project>");

        javaFile("regression-core", "com/aqa/core/Marker.java", "package com.aqa.core;\n\nclass Marker { }\n");

        javaFile("regression-jhipster", "com/aqa/jhipster/ui/models/Coordinates.java", """
                package com.aqa.jhipster.ui.models;

                public class Coordinates {
                    private final int x;
                    private final int y;

                    public Coordinates(final int x, final int y) {
                        this.x = x;
                        this.y = y;
                    }

                    public int getX() {
                        return x;
                    }

                    public int getY() {
                        return y;
                    }
                }
                """);

        javaFile("regression-nextjs-commerce", "com/aqa/nextjscommerce/pages/BadPage.java", """
                package com.aqa.nextjscommerce.pages;

                import org.openqa.selenium.By;

                public final class BadPage {
                    private By locator = By.cssSelector("main h1");
                }
                """);
    }

    private void javaFile(String module, String relativePath, String content) throws Exception {
        Path modulePom = temporaryDirectory.resolve(module).resolve("pom.xml");
        Files.createDirectories(modulePom.getParent());
        if (Files.notExists(modulePom)) {
            Files.writeString(modulePom, "<project/>");
        }
        Path file = temporaryDirectory.resolve(module).resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
