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

class ModuleBoundariesToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsARealMod001ViolationWhenNoFilterIsGiven() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of());

        Map<String, Object> jhipster = moduleResult(data, "regression-jhipster");
        assertThat(rulesApplied(jhipster)).containsExactlyInAnyOrder("MOD-001", "MOD-002", "MOD-003", "MOD-004");
        List<Map<String, Object>> violations = violations(jhipster);
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().get("ruleId")).isEqualTo("MOD-001");
        assertThat(violations.getFirst().get("module")).isEqualTo("regression-jhipster");

        assertThat(violations(moduleResult(data, "regression-core"))).isEmpty();
        assertThat(violations(moduleResult(data, "regression-petstore-api"))).isEmpty();
    }

    @Test
    void reportsNoViolationsForAModuleWithNoForbiddenImports() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of("module", "regression-core"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(violations(modules.getFirst())).isEmpty();
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
    void filtersByProfileAloneAcrossAllDeclaredModules() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of("profile", "API"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        assertThat(modules).extracting(module -> module.get("module")).containsExactly("regression-petstore-api");
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
        SyncToolSpecification tool = ModuleBoundariesTool.tool(temporaryDirectory, this::moduleTypeByName);
        return tool.callHandler().apply(null, new CallToolRequest(ModuleBoundariesTool.TOOL_NAME, arguments));
    }

    private Map<String, String> moduleTypeByName() {
        return Map.of("regression-core", "CORE", "regression-petstore-api", "API", "regression-jhipster", "API_UI");
    }

    /** A minimal, real 3-module reactor: regression-jhipster imports regression-petstore-api's basePackage,
     * a genuine MOD-001 sibling-module violation; regression-core and regression-petstore-api stay clean. */
    private void reactor() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>"
                + "<module>regression-core</module><module>regression-petstore-api</module><module>regression-jhipster</module>"
                + "</modules></project>");

        javaFile("regression-core", "com/aqa/core/Marker.java", "package com.aqa.core;\n\nclass Marker { }\n");
        javaFile("regression-petstore-api", "com/aqa/petstore/api/PetstoreApi.java",
                "package com.aqa.petstore.api;\n\nclass PetstoreApi { }\n");
        javaFile("regression-jhipster", "com/aqa/jhipster/api/services/Consumer.java", """
                package com.aqa.jhipster.api.services;

                import com.aqa.petstore.api.PetstoreApi;

                class Consumer { }
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
